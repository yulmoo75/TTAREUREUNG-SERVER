package com.ttareureung.api.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medication_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time_slot", nullable = false, length = 20)
    private MealTimeSlot mealTimeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedicationStatus status;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    public MedicationLog(Medication medication, LocalDate targetDate, MealTimeSlot mealTimeSlot) {
        this.medication = medication;
        this.targetDate = targetDate;
        this.mealTimeSlot = mealTimeSlot;
        this.status = MedicationStatus.PENDING;
    }

    public static MedicationLog create(Medication medication, LocalDate targetDate, MealTimeSlot mealTimeSlot) {
        return new MedicationLog(medication, targetDate, mealTimeSlot);
    }

    public void changeStatus(MedicationStatus newStatus) {
        this.status = newStatus;
        this.takenAt = (newStatus == MedicationStatus.TAKEN) ? LocalDateTime.now() : null;
    }
}
