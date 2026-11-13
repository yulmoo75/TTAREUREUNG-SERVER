package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Profile;
import io.swagger.v3.oas.annotations.media.Schema;

// 연결 정보에 함께 내려주는 상대 프로필 요약
public record ProfileSummary(
        @Schema(example = "2") Long profileId,
        @Schema(example = "김순자") String name,
        @Schema(example = "어머니") String relationship
) {
    public static ProfileSummary from(Profile profile) {
        return new ProfileSummary(profile.getId(), profile.getName(), profile.getRelationship());
    }
}
