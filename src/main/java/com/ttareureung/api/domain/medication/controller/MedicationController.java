package com.ttareureung.api.domain.medication.controller;

import com.ttareureung.api.domain.medication.dto.*;
import com.ttareureung.api.domain.medication.service.MedicationService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/medications")
@Tag(name = "Medication", description = "약 복용 일정")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @GetMapping("/schedule")
    @Operation(summary = "일일 약 복용 일정 조회", description = "특정 날짜의 시니어 약 복용 스케줄을 조회합니다.")
    public ResponseEntity<BaseResponse<DailyScheduleResponse>> getDailySchedule(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("senior_id") Long seniorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailyScheduleResponse response = medicationService.getDailySchedule(accountId(jwt), seniorId, date);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @PatchMapping("/logs/{logId}")
    @Operation(summary = "약 복용 로그 상태 변경", description = "복용 완료 또는 미복용 상태로 변경합니다.")
    public ResponseEntity<BaseResponse<UpdateLogStatusResponse>> updateLogStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long logId,
            @RequestBody @Valid UpdateLogStatusRequest request) {
        UpdateLogStatusResponse response = medicationService.updateLogStatus(accountId(jwt), logId, request.status());
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "약 복용 일정 등록", description = "시니어에게 약을 등록하고 기간 × 끼니만큼 복용 로그를 생성합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류, 시니어 아님, 기간 오류 (MEDICATION_003~005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 시니어 또는 접근 불가 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    public ResponseEntity<BaseResponse<MedicationCreateResponse>> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MedicationCreateRequest request) {
        MedicationCreateResponse response = medicationService.register(accountId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "약 복용 일정 대량 등록", description = "여러 개의 약을 한 번에 등록합니다.")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<BaseResponse<MedicationBulkCreateResponse>> registerBulk(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid MedicationBulkCreateRequest request) {
        MedicationBulkCreateResponse response = medicationService.registerBulk(accountId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "등록된 약 목록 조회", description = "시니어에게 등록된 전체 약 목록을 조회합니다.")
    public ResponseEntity<BaseResponse<MedicationListResponse>> getMedications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("senior_id") Long seniorId) {
        MedicationListResponse response = medicationService.getMedications(accountId(jwt), seniorId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @PutMapping(value = "/{medicationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "약 정보 수정", description = "등록된 약의 정보를 수정합니다.")
    public ResponseEntity<BaseResponse<MedicationUpdateResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long medicationId,
            @RequestBody @Valid MedicationUpdateRequest request) {
        MedicationUpdateResponse response = medicationService.updateMedication(accountId(jwt), medicationId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @DeleteMapping("/{medicationId}")
    @Operation(summary = "약 단건 삭제", description = "등록된 약을 삭제합니다.")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long medicationId) {
        medicationService.deleteMedication(accountId(jwt), medicationId);
        return ResponseEntity.ok(BaseResponse.successWithoutData());
    }

    @DeleteMapping
    @Operation(summary = "약 일괄 삭제", description = "선택한 약을 일괄 삭제합니다.")
    public ResponseEntity<BaseResponse<MedicationBulkDeleteResponse>> deleteBulk(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("medication_ids") List<Long> medicationIds) {
        if (medicationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제할 약을 선택해주세요.");
        }
        MedicationBulkDeleteResponse response = medicationService.deleteMedications(accountId(jwt), medicationIds);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}