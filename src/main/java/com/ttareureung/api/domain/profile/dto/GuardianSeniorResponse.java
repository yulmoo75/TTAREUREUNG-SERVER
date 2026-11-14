package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import io.swagger.v3.oas.annotations.media.Schema;

public record GuardianSeniorResponse(
        @Schema(example = "1") Long linkId,
        @Schema(example = "true") boolean isPrimary,
        ProfileSummary guardian,
        ProfileSummary senior
) {
    public static GuardianSeniorResponse from(GuardianSenior link) {
        return new GuardianSeniorResponse(
                link.getId(),
                link.isPrimary(),
                ProfileSummary.from(link.getGuardian()),
                ProfileSummary.from(link.getSenior())
        );
    }
}