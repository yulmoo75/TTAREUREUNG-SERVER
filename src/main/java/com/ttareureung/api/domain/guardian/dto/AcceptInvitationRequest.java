package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @JsonProperty("guardian_profile_id") @NotNull Long guardianProfileId) {

}
