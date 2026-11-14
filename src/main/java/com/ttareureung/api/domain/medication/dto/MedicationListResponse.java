package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.CycleType;
import com.ttareureung.api.domain.medication.entity.MealTimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MedicationListResponse(List<MedicationItem> medications) {
    public record MedicationItem(
            @JsonProperty("medication_id") Long medicationId,
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("custom_drug_name") String customDrugName,
            @JsonProperty("image_url") String imageUrl,
            String effect,
            @JsonProperty("storage_location") String storageLocation,
            @JsonProperty("intake_reason") String intakeReason,
            String frequency,
            @JsonProperty("cycle_type") CycleType cycleType,
            @JsonProperty("meal_time_slots") List<MealTimeSlot> mealTimeSlots,
            @JsonProperty("notify_on_miss") boolean notifyOnMiss,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
