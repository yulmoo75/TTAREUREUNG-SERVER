package com.ttareureung.api.domain.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record GuardianSeniorCreateRequest(
        @NotNull(message = "보호자 프로필을 선택해주세요.")
        @Schema(example = "1")
        Long guardianProfileId,

        @NotNull(message = "시니어 프로필을 선택해주세요.")
        @Schema(example = "2")
        Long seniorProfileId
) {
}
