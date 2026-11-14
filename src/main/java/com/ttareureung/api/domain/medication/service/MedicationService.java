package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.Medicine;
import com.ttareureung.api.domain.medication.dto.DailyScheduleResponse.TimeSlot;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import com.ttareureung.api.domain.medication.entity.Medication;
import com.ttareureung.api.domain.medication.entity.MedicationLog;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.medication.repository.MedicationRepository;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.security.AccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private static final long MAX_PERIOD_DAYS = 366;  // 1년으로 가정

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;
    private final ProfileRepository profileRepository;
    private final SeniorAccessValidator seniorAccessValidator;
    private final AccessChecker accessChecker;

    @Transactional(readOnly = true)
    public DailyScheduleResponse getDailySchedule(Long accountId, Long seniorId, LocalDate date) {
        seniorAccessValidator.requireSenior(accountId, seniorId);
        List<MedicationLog> logs = medicationLogRepository.findDailyLogs(seniorId, date);

        Map<MealTimeSlot, List<MedicationLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(MedicationLog::getMealTimeSlot, TreeMap::new, Collectors.toList()));

        List<TimeSlot> timeSlots = grouped.entrySet().stream()
                .map(e -> toTimeSlot(e.getKey(), e.getValue()))
                .toList();

        return new DailyScheduleResponse(date, timeSlots);
    }

    @Transactional
    public UpdateLogStatusResponse updateLogStatus(Long accountId, Long logId, MedicationStatus status) {
        accessChecker.activeAccount(accountId);
        MedicationLog log = medicationLogRepository.findById(logId)
                .orElseThrow(() -> new CustomException(MedicationErrorCode.MEDICATION_LOG_NOT_FOUND));

        if (!seniorAccessValidator.hasAccess(accountId, log.getMedication().getSeniorProfileId())) {
            throw new CustomException(MedicationErrorCode.MEDICATION_LOG_NOT_FOUND);
        }

        log.changeStatus(status);
        return new UpdateLogStatusResponse(log.getId(), log.getStatus(), "복용 상태가 변경되었습니다.");
    }

    @Transactional
    public MedicationCreateResponse register(Long accountId, MedicationCreateRequest req) {
        accessChecker.activeAccount(accountId);
        seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());
        Long id = createOne(req.seniorProfileId(), req.toItem());
        return new MedicationCreateResponse(id, "약 복용 일정이 등록되어 있습니다.");
    }

    @Transactional // 하나라도 실패하면 전체 롤백
    public MedicationBulkCreateResponse registerBulk(Long accountId, MedicationBulkCreateRequest req) {
        accessChecker.activeAccount(accountId);
        seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());
        List<Long> ids = req.medications().stream()
                .map(item -> createOne(req.seniorProfileId(), item))
                .toList();
        return new MedicationBulkCreateResponse(ids, ids.size() + "개의 약 복용 일정이 등록되었습니다.");
    }

    @Transactional
    public MedicationBulkDeleteResponse deleteMedications(Long accountId, List<Long> medicationIds) {
        accessChecker.activeAccount(accountId);
        List<Long> ids = medicationIds.stream().distinct().toList();

        List<Medication> found = medicationRepository.findAllById(ids);
        if (found.size() != ids.size()
                || !found.stream().map(Medication::getSeniorProfileId).distinct()
                .allMatch(sid -> seniorAccessValidator.hasAccess(accountId, sid))) {
            throw new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND);
        }

        medicationLogRepository.deleteByMedicationIds(ids);
        medicationRepository.deleteAllByIdInBatch(ids);
        return new MedicationBulkDeleteResponse(ids.size(), ids.size() + "개의 약 복용 일정이 삭제되었습니다.");
    }

    @Transactional(readOnly = true)
    public MedicationListResponse getMedications(Long accountId, Long seniorId) {
        seniorAccessValidator.requireSenior(accountId, seniorId);
        List<Medication> medications = medicationRepository.findBySeniorProfileIdOrderByCreatedAtDesc(seniorId);

        Map<Long, List<MealTimeSlot>> slots = new HashMap<>();
        if (!medications.isEmpty()) {
            for (Object[] row : medicationLogRepository.findSlotsByMedicationIds(
                    medications.stream().map(Medication::getId).toList())) {
                slots.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((MealTimeSlot) row[1]);
            }
            slots.values().forEach(Collections::sort); // 아침 점심 저녁
        }
        return new MedicationListResponse(medications.stream()
                .map(m -> new MedicationListResponse.MedicationItem(
                        m.getId(), m.getDrugName(), m.getCustomDrugName(), m.getImageUrl(), m.getEffect(),
                        m.getStorageLocation(), m.getIntakeReason(), m.getFrequency(), m.getCycleType(),
                        slots.getOrDefault(m.getId(), List.of()), m.isNotifyOnMiss(),
                        m.getStartDate(), m.getEndDate(), m.getCreatedAt()))
                .toList());
    }

    @Transactional
    public MedicationUpdateResponse updateMedication(Long accountId, Long medicationId, MedicationUpdateRequest req) {
        accessChecker.activeAccount(accountId);
        Medication m = findOwnedMedication(accountId, medicationId);
        m.update(req.customDrugName(), req.frequency(), req.effect(), req.imageUrl(),
                req.notifyOnMiss(), req.storageLocation(), req.intakeReason());
        return new MedicationUpdateResponse(m.getId(), "약 정보가 수정되었습니다.");
    }

    @Transactional
    public void deleteMedication(Long accountId, Long medicationId) {
        accessChecker.activeAccount(accountId);
        Medication m = findOwnedMedication(accountId, medicationId);
        medicationLogRepository.deleteByMedicationIds(List.of(medicationId));
        medicationRepository.delete(m);
    }

    private Medication findOwnedMedication(Long accountId, Long medicationId) {
        Medication m = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND));
        if (!seniorAccessValidator.hasAccess(accountId, m.getSeniorProfileId())) {
            throw new CustomException(MedicationErrorCode.MEDICATION_NOT_FOUND);
        }
        return m;
    }

    private Long createOne(Long seniorProfileId, MedicationItemRequest item) {
        if (item.endDate().isBefore(item.startDate())) {
            throw new CustomException(MedicationErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(item.startDate(), item.endDate()) > MAX_PERIOD_DAYS) {
            throw new CustomException(MedicationErrorCode.PERIOD_TOO_LONG);
        }

        Medication medication = medicationRepository.save(Medication.builder()
                .seniorProfileId(seniorProfileId)
                .drugName(item.drugName())
                .customDrugName(item.customDrugName())
                .imageUrl(item.imageUrl())
                .effect(item.effect())
                .storageLocation(item.storageLocation())
                .intakeReason(item.intakeReason())
                .startDate(item.startDate())
                .endDate(item.endDate())
                .cycleType(item.cycleType())
                .frequency(item.frequency())
                .notifyOnMiss(item.notifyOnMiss())
                .build());

        List<MealTimeSlot> slots = item.mealTimeSlots().stream().distinct().toList();
        List<MedicationLog> logs = new ArrayList<>();
        for (LocalDate d = item.startDate(); !d.isAfter(item.endDate()); d = d.plusDays(1)) {
            for (MealTimeSlot slot : slots) {
                logs.add(MedicationLog.create(medication, d, slot));
            }
        }
        medicationLogRepository.saveAll(logs);
        return medication.getId();
    }

    private TimeSlot toTimeSlot(MealTimeSlot slot, List<MedicationLog> logs) {
        List<Medicine> medicines = logs.stream()
                .map(l -> new Medicine(l.getId(), l.getMedication().getId(),
                        l.getMedication().getDisplayName(), l.getStatus()))
                .toList();

        return new TimeSlot(slot.name(), slot.getLabel(), calcSlotStatus(logs), medicines);
    }

    private String calcSlotStatus(List<MedicationLog> logs) {
        long taken = logs.stream().filter(l -> l.getStatus() == MedicationStatus.TAKEN).count();
        long missed = logs.stream().filter(l -> l.getStatus() == MedicationStatus.MISSED).count();

        if (taken == logs.size()) return "TAKEN";
        if (missed == logs.size()) return "MISSED";
        if (taken > 0) return "PARTIAL";
        return "PENDING";
    }
}