package com.ttareureung.api.domain.medication.service;

import com.ttareureung.api.domain.medication.repository.MedicationLogRepository;
import com.ttareureung.api.domain.medication.repository.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicationCleanupService {

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;

    // 프로필 삭제 트랜잭션 안에서만 호출 가능
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllBySenior(Long seniorProfileId) {
        medicationLogRepository.deleteAllBySeniorProfileId(seniorProfileId);
        medicationRepository.deleteAllBySeniorProfileId(seniorProfileId);
    }
}
