package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record InvitationRequest(
        @JsonProperty("senior_profile_id") @NotNull Long seniorProfileId,
        @JsonProperty("phone_numbers") @NotEmpty @Size(max = 10)
        List<@Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$") String> phoneNumbers) {
    
}
