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
            @RequestParam("account_id") Long accountId,
            @RequestParam("senior_id") Long seniorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BaseResponse.success(
                medicationService.getDailySchedule(accountId, seniorId, date));
    }

    @PatchMapping("/logs/{logId}")
    public BaseResponse<UpdateLogStatusResponse> updateLogStatus(
            @RequestParam("account_id") Long accountId,
            @PathVariable Long logId,
            @RequestBody @Valid UpdateLogStatusRequest request) {
        return BaseResponse.success(medicationService.updateLogStatus(accountId, logId, request.status()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<MedicationCreateResponse> register(
            @RequestParam("account_id") Long accountId,
            @RequestBody @Valid MedicationCreateRequest request) {
        return BaseResponse.success(medicationService.register(accountId, request));
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<MedicationBulkCreateResponse> registerBulk(
            @RequestParam("account_id") Long accountId,
            @RequestBody @Valid MedicationBulkCreateRequest request) {
        return BaseResponse.success(medicationService.registerBulk(accountId, request));
    }

    @GetMapping
    public BaseResponse<MedicationListResponse> getMedications(
            @RequestParam("account_id") Long accountId,
            @RequestParam("senior_id") Long seniorId) {
        return BaseResponse.success(medicationService.getMedications(accountId, seniorId));
    }

    @PutMapping("/{medicationId}")
    public BaseResponse<MedicationUpdateResponse> update(
            @RequestParam("account_id") Long accountId,
            @PathVariable Long medicationId,
            @RequestBody @Valid MedicationUpdateRequest request) {
        return BaseResponse.success(medicationService.updateMedication(accountId, medicationId, request));
    }

    @DeleteMapping("/{medicationId}")
    public BaseResponse<Void> delete(
            @RequestParam("account_id") Long accountId,
            @PathVariable Long medicationId) {
        medicationService.deleteMedication(accountId, medicationId);
        return BaseResponse.successWithoutData();
    }

    @DeleteMapping
    public BaseResponse<MedicationBulkDeleteResponse> deleteBulk(
            @RequestParam("account_id") Long accountId,
            @RequestParam("medication_ids") List<Long> medicationIds) {
        if (medicationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제할 약을 선택해주세요.");
        }
        return BaseResponse.success(medicationService.deleteMedications(accountId, medicationIds));
    }
}