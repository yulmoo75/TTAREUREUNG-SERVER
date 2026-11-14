import com.ttareureung.api.domain.medication.entity.CycleType;
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

    @Column(name = "storage_location", length = 100)
    private String storageLocation;

    @Column(name = "intake_reason", columnDefinition = "TEXT")
    private String intakeReason;

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
                       String effect, String storageLocation, String intakeReason, LocalDate startDate,
                       LocalDate endDate, CycleType cycleType, String frequency, boolean notifyOnMiss) {
        this.seniorProfileId = seniorProfileId;
        this.drugName = drugName;
        this.customDrugName = customDrugName;
        this.imageUrl = imageUrl;
        this.effect = effect;
        this.storageLocation = storageLocation;
        this.intakeReason = intakeReason;
        this.startDate = startDate;
        this.endDate = endDate;
        this.cycleType = cycleType;
        this.frequency = frequency;
        this.notifyOnMiss = notifyOnMiss;
    }

    public String getDisplayName() {
        return (customDrugName != null && !customDrugName.isBlank()) ? customDrugName : drugName;
    }

    /** null로 온 필드는 유지, 복용 이유는 빈 문자열이면 삭제 */
    public void update(String customDrugName, String frequency, String effect, String imageUrl,
                       Boolean notifyOnMiss, String storageLocation, String intakeReason) {
        if (customDrugName != null) this.customDrugName = customDrugName;
        if (frequency != null) this.frequency = frequency;
        if (effect != null) this.effect = effect;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (notifyOnMiss != null) this.notifyOnMiss = notifyOnMiss;
        if (storageLocation != null) this.storageLocation = storageLocation;
        if (intakeReason != null) this.intakeReason = intakeReason.isEmpty() ? null : intakeReason;
    }
}