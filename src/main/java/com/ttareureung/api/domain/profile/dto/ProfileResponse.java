package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Gender;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record ProfileResponse(
        @Schema(example = "1") Long profileId,
        @Schema(example = "김순자") String name,
        @Schema(example = "1950-03-15") LocalDate birthDate,
        @Schema(example = "FEMALE") Gender gender,
        @Schema(example = "010-1234-5678") String phoneNumber,
        @Schema(example = "어머니") String relationship,
        @Schema(example = "SENIOR") ProfileRole role,
        @Schema(description = "보호자면 담당 시니어, 시니어면 담당 보호자") List<ProfileSummary> linkedProfiles
) {
    public static ProfileResponse of(Profile profile, List<ProfileSummary> linkedProfiles) {
        return new ProfileResponse(profile.getId(), profile.getName(), profile.getBirthDate(),
                profile.getGender(), profile.getPhoneNumber(), profile.getRelationship(), profile.getRole(),
                linkedProfiles);
    }
}
