package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import io.swagger.v3.oas.annotations.media.Schema;

public record GuardianSeniorResponse(
        @Schema(example = "1") Long linkId,
        ProfileSummary guardian,
        ProfileSummary senior
) {
    public static GuardianSeniorResponse from(GuardianSenior link) {
        return new GuardianSeniorResponse(link.getId(),
                ProfileSummary.from(link.getGuardian()), ProfileSummary.from(link.getSenior()));
    }
}
