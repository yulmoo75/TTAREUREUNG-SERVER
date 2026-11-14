package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.CycleType;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MedicationItemRequest(
        @JsonProperty("drug_name") @NotBlank @Size(max = 100) String drugName,
        @JsonProperty("custom_drug_name") @Size(max = 100) String customDrugName,
        @JsonProperty("image_url") @Size(max = 255) String imageUrl,
        String effect,
        @JsonProperty("storage_location") @NotBlank @Size(max = 100) String storageLocation,
        @JsonProperty("intake_reason") @Size(max = 1000) String intakeReason,
        @JsonProperty("start_date") @NotNull LocalDate startDate,
        @JsonProperty("end_date") @NotNull LocalDate endDate,
        @JsonProperty("cycle_type") @NotNull CycleType cycleType,
        @NotBlank @Size(max = 100) String frequency,
        @JsonProperty("notify_on_miss") @NotNull Boolean notifyOnMiss,
        @JsonProperty("meal_time_slots") @NotEmpty List<MealTimeSlot> mealTimeSlots
) {
    
}
