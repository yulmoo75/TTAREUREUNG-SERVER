package com.ttareureung.api.domain.medication.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MedicationErrorCode implements ErrorCode {
    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICATION_001", "약 일정을 찾을 수 없습니다."),
    MEDICATION_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICATION_002", "복용 기록을 찾을 수 없습니다."),
    NOT_SENIOR_PROFILE(HttpStatus.BAD_REQUEST, "MEDICATION_003", "시니어 프로필이 아닙니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "MEDICATION_004", "종료일은 시작일 이후여야 합니다."),
    PERIOD_TOO_LONG(HttpStatus.BAD_REQUEST, "MEDICATION_005", "복용 기간은 최대 1년까지 등록할 수 있습니다."),
    DELETE_TARGET_EMPTY(HttpStatus.BAD_REQUEST, "MEDICATION_006", "삭제할 약을 선택해주세요."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDICATION_007", "JPG, PNG, WEBP, PDF 파일만 올릴 수 있습니다."),
    NO_MEDICATION_RECOGNIZED(HttpStatus.UNPROCESSABLE_ENTITY, "MEDICATION_008", "약 정보를 찾지 못했습니다. 직접 입력해주세요."),
    AI_RECOGNITION_FAILED(HttpStatus.BAD_GATEWAY, "MEDICATION_009", "AI 인식에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "MEDICATION_010", "AI 응답을 해석하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}