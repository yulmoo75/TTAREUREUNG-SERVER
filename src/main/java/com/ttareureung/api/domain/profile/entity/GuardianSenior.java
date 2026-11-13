package com.ttareureung.api.domain.profile.entity;

import com.ttareureung.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 보호자 프로필과 시니어 프로필의 담당 관계.
 * 같은 가족 계정 안의 프로필끼리만 연결, 계정 일치 여부는 서비스에서 검증
 */
@Getter
@Entity
@Table(name = "guardian_seniors", uniqueConstraints = @UniqueConstraint(
        name = "uk_guardian_seniors_pair", columnNames = {"guardian_profile_id", "senior_profile_id"}))
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianSenior extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_profile_id", nullable = false)
    private Profile guardian;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private Profile senior;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GuardianSeniorStatus status;

    public GuardianSenior(Profile guardian, Profile senior) {
        this.guardian = guardian;
        this.senior = senior;
        this.status = GuardianSeniorStatus.ACTIVE;
    }

    // 프로필 목록에서 "OOO 시니어 담당"처럼 상대 프로필 표시용
    public Profile counterpartOf(Long profileId) {
        return guardian.getId().equals(profileId) ? senior : guardian;
    }

    public boolean involves(Long profileId) {
        return guardian.getId().equals(profileId) || senior.getId().equals(profileId);
    }
}
