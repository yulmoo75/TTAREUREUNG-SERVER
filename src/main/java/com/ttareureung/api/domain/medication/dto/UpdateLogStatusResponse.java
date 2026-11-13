package com.ttareureung.api.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttareureung.api.domain.medication.entity.MedicationStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UpdateLogStatusResponse {
    @JsonProperty("log_id")
    private final Long logId;
    private final MedicationStatus status;
    private final String message;
}
