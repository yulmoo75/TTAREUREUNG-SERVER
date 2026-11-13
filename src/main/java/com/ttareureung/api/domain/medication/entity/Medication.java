package com.ttareureung.api.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_profile_id", nullable = false)
    private Long seniorProfileId;

    @Column(name = "drug_name", nullable = false, length = 100)
    private String drugName;

    @Column(name = "custom_drug_name", length = 100)
    private String customDrugName;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String effect;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 20)
    private CycleType cycleType;

    @Column(nullable = false, length = 100)
    private String frequency;

    @Column(name = "notify_on_miss", nullable = false)
    private boolean notifyOnMiss;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Medication(Long seniorProfileId, String drugName, String customDrugName, String imageUrl,
                       String effect, LocalDate startDate, LocalDate endDate, CycleType cycleType,
                       String frequency, boolean notifyOnMiss) {
        this.seniorProfileId = seniorProfileId;
        this.drugName = drugName;
        this.customDrugName = customDrugName;
        this.imageUrl = imageUrl;
        this.effect = effect;
        this.startDate = startDate;
        this.endDate = endDate;
        this.cycleType = cycleType;
        this.frequency = frequency;
        this.notifyOnMiss = notifyOnMiss;
    }

    public String getDisplayName() {
        return (customDrugName != null && !customDrugName.isBlank()) ? customDrugName : drugName;
    }

    // null 필드 변경 x
    public void update(String customDrugName, String frequency, String effect,
                       String imageUrl, Boolean notifyOnMiss) {
        if (customDrugName != null) this.customDrugName = customDrugName;
        if (frequency != null) this.frequency = frequency;
        if (effect != null) this.effect = effect;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (notifyOnMiss != null) this.notifyOnMiss = notifyOnMiss;
    }
}