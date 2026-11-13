package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MedicationBulkCreateRequest(
        @JsonProperty("senior_profile_id") @NotNull Long seniorProfileId,
        @NotEmpty @Size(max = 20) List<@Valid MedicationItemRequest> medications
) {

}
