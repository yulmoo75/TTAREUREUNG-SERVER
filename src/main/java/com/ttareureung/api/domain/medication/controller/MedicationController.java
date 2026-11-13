package com.ttareureung.api.domain.medication.controller;

import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.service.MedicationService;
import com.ttareureung.api.global.common.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping("/schedule")
    public BaseResponse<DailyScheduleResponse> getDailySchedule(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("senior_id") Long seniorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BaseResponse.success(
                medicationService.getDailySchedule(AuthPrincipals.accountId(jwt), seniorId, date));
    }

    @PatchMapping("/logs/{logId}")
    public BaseResponse<UpdateLogStatusResponse> updateLogStatus(
            @PathVariable Long logId,
            @RequestBody @Valid UpdateLogStatusRequest request) {
        return BaseResponse.success(medicationService.updateLogStatus(logId, request.status()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<MedicationCreateResponse> register(@RequestBody @Valid MedicationCreateRequest request) {
        return BaseResponse.success(medicationService.register(request));
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<MedicationBulkCreateResponse> registerBulk(@RequestBody @Valid MedicationBulkCreateRequest request) {
        return BaseResponse.success(medicationService.registerBulk(request));
    }

    @GetMapping
    public BaseResponse<MedicationListResponse> getMedications(@RequestParam("senior_id") Long seniorId) {
        return BaseResponse.success(medicationService.getMedications(seniorId));
    }

    @PutMapping("/{medicationId}")
    public BaseResponse<MedicationUpdateResponse> update(@PathVariable Long medicationId,
                                                         @RequestBody @Valid MedicationUpdateRequest request) {
        return BaseResponse.success(medicationService.updateMedication(medicationId, request));
    }

    @DeleteMapping("/{medicationId}")
    public BaseResponse<Void> delete(@PathVariable Long medicationId) {
        medicationService.deleteMedication(medicationId); // 서비스가 void를 반환하도록 수정 필!!
        return BaseResponse.successWithoutData();
    }

    @DeleteMapping
    public BaseResponse<MedicationBulkDeleteResponse> deleteBulk(@RequestParam("medication_ids") List<Long> medicationIds) {
        if (medicationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제할 약을 선택해주세요.");
        }
        return BaseResponse.success(medicationService.deleteMedications(medicationIds));
    }

}