package com.ttareureung.api.domain.guardian.exception;

import com.ttareureung.api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GuardianErrorCode implements ErrorCode {
    GUARDIAN_NOT_CONNECTED(HttpStatus.NOT_FOUND, "GUARDIAN_001", "해당 시니어와 연결된 보호자가 아닙니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "GUARDIAN_002", "유효하지 않거나 이미 사용된 초대입니다."),
    INVITATION_EXPIRED(HttpStatus.GONE, "GUARDIAN_003", "만료된 초대입니다."),
    NOT_GUARDIAN_PROFILE(HttpStatus.BAD_REQUEST, "GUARDIAN_004", "보호자 프로필로만 초대를 수락할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
