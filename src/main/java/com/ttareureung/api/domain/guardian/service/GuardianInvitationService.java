package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.dto.AcceptInvitationRequest;
import com.ttareureung.api.domain.guardian.dto.AcceptInvitationResponse;
import com.ttareureung.api.domain.guardian.dto.InvitationRequest;
import com.ttareureung.api.domain.guardian.dto.InvitationResponse;
import com.ttareureung.api.domain.guardian.entity.GuardianInvitation;
import com.ttareureung.api.domain.guardian.entity.InvitationStatus;
import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianInvitationRepository;
import com.ttareureung.api.domain.guardian.repository.GuardianSeniorRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.security.ProfileAccessChecker;
import com.ttareureung.api.global.util.PhoneNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GuardianInvitationService {

    private final ProfileAccessChecker accessChecker;
    private final SeniorAccessValidator seniorAccessValidator;
    private final GuardianInvitationRepository invitations;
    private final GuardianSeniorRepository links;
    private final ProfileRepository profiles;

    public GuardianInvitationService(ProfileAccessChecker accessChecker,
                                     SeniorAccessValidator seniorAccessValidator,
                                     GuardianInvitationRepository invitations,
                                     GuardianSeniorRepository links,
                                     ProfileRepository profiles) {
        this.accessChecker = accessChecker;
        this.seniorAccessValidator = seniorAccessValidator;
        this.invitations = invitations;
        this.links = links;
        this.profiles = profiles;
    }

    @Transactional
    public InvitationResponse invite(Long accountId, InvitationRequest req) {
        accessChecker.activeAccount(accountId);
        Profile senior = seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());

        Set<String> linkedPhones = links.findAllByProfileId(senior.getId()).stream()
                .map(l -> PhoneNumbers.normalize(l.getGuardian().getPhoneNumber()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int invited = 0;
        for (String phone : req.phoneNumbers().stream().map(PhoneNumbers::normalize).distinct().toList()) {
            if (linkedPhones.contains(phone) || invitations.existsBySeniorProfileIdAndPhoneNumberAndStatus(
                    senior.getId(), phone, InvitationStatus.PENDING)) {
                continue;
            }
            GuardianInvitation invitation = invitations.save(GuardianInvitation.create(senior.getId(), phone));
            // SMS 발송 ?
            invited++;
        }
        return new InvitationResponse(invited, "초대 링크를 전송했습니다.");
    }

    /** 초대 링크를 연 사람이 가입 → 보호자 프로필 생성 후 토큰과 함께 호출 */
    @Transactional
    public AcceptInvitationResponse accept(Long accountId, AcceptInvitationRequest req) {
        accessChecker.activeAccount(accountId);

        GuardianInvitation invitation = invitations.findByToken(req.token())
                .filter(i -> i.getStatus() == InvitationStatus.PENDING)
                .orElseThrow(() -> new CustomException(GuardianErrorCode.INVITATION_NOT_FOUND));
        if (invitation.isExpired()) {
            throw new CustomException(GuardianErrorCode.INVITATION_EXPIRED);
        }

        Profile guardian = accessChecker.ownedProfile(accountId, req.guardianProfileId());
        if (guardian.getRole() != ProfileRole.GUARDIAN) {
            throw new CustomException(GuardianErrorCode.NOT_GUARDIAN_PROFILE);
        }
        Profile senior = profiles.findById(invitation.getSeniorProfileId())
                .orElseThrow(() -> new CustomException(GuardianErrorCode.INVITATION_NOT_FOUND));

        if (!links.existsByGuardianIdAndSeniorId(guardian.getId(), senior.getId())) {
            links.save(new GuardianSenior(guardian, senior));
        }
        invitation.accept();
        return new AcceptInvitationResponse(senior.getId());
    }
}