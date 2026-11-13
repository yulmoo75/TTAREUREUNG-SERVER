package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PrimaryGuardianRequest(
        @JsonProperty("senior_profile_id") @NotNull Long seniorProfileId,
        @JsonProperty("guardian_id") @NotNull Long guardianId) {

}
