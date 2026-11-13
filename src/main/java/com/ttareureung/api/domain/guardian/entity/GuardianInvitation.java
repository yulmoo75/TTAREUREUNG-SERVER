package com.ttareureung.api.domain.guardian.entity;

import com.ttareureung.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guardian_invitations")
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianInvitation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_profile_id", nullable = false)
    private Long seniorProfileId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber; // 숫자만

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static GuardianInvitation create(Long seniorProfileId, String normalizedPhone) {
        GuardianInvitation i = new GuardianInvitation();
        i.seniorProfileId = seniorProfileId;
        i.phoneNumber = normalizedPhone;
        i.token = UUID.randomUUID().toString().replace("-", "");
        i.status = InvitationStatus.PENDING;
        i.expiresAt = LocalDateTime.now().plusDays(7);
        return i;
    }

    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }
    public void accept() { this.status = InvitationStatus.ACCEPTED; }
}