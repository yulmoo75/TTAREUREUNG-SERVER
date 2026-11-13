package com.ttareureung.api.domain.guardian.repository;

import com.ttareureung.api.domain.guardian.entity.GuardianInvitation;
import com.ttareureung.api.domain.guardian.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuardianInvitationRepository extends JpaRepository<GuardianInvitation, Long> {
    boolean existsBySeniorProfileIdAndPhoneNumberAndStatus(Long seniorProfileId, String phoneNumber,
                                                           InvitationStatus status);
    Optional<GuardianInvitation> findByToken(String token);
}
