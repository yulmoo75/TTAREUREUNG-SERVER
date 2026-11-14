package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record MedicationUpdateRequest(
        @JsonProperty("custom_drug_name") @Size(max = 100) String customDrugName,
        @Size(max = 100) String frequency,
        String effect,
        @JsonProperty("image_url") @Size(max = 255) String imageUrl,
        @JsonProperty("notify_on_miss") Boolean notifyOnMiss,
        @JsonProperty("storage_location") @Pattern(regexp = ".*\\S.*") @Size(max = 100) String storageLocation,
        @JsonProperty("intake_reason") @Size(max = 1000) String intakeReason
) {
    
}
