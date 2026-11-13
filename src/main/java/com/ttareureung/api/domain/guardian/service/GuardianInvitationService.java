package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.dto.AcceptInvitationRequest;
import com.ttareureung.api.domain.guardian.dto.AcceptInvitationResponse;
import com.ttareureung.api.domain.guardian.dto.InvitationRequest;
import com.ttareureung.api.domain.guardian.dto.InvitationResponse;
import com.ttareureung.api.domain.guardian.entity.GuardianInvitation;
import com.ttareureung.api.domain.guardian.entity.InvitationStatus;
import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianInvitationRepository;
import com.ttareureung.api.domain.guardian.repository.GuardianSeniorQueryRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuardianInvitationService {

    private final GuardianInvitationRepository invitationRepository;
    private final GuardianSeniorQueryRepository guardianSeniorQueryRepository;
    private final ProfileRepository profileRepository;
    private final SeniorAccessValidator seniorAccessValidator;

    @Transactional
    public InvitationResponse invite(Long accountId, InvitationRequest req) {
        Profile senior = seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());

        Set<String> linkedPhones = guardianSeniorQueryRepository
                .findGuardiansOfSenior(senior.getId(), GuardianSeniorStatus.ACTIVE).stream()
                .map(gs -> normalizePhone(gs.getGuardian().getPhoneNumber()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int invited = 0;
        for (String rawPhone : req.phoneNumbers()) {
            String phone = normalizePhone(rawPhone);
            if (phone == null) continue;

            if (linkedPhones.contains(phone) || invitationRepository
                    .existsBySeniorProfileIdAndPhoneNumberAndStatus(senior.getId(), phone, InvitationStatus.PENDING)) {
                continue;
            }
            invitationRepository.save(GuardianInvitation.create(senior.getId(), phone));
            // SMS 발송 ?
            invited++;
        }
        return new InvitationResponse(invited, "초대 링크를 전송했습니다.");
    }

    // 초대 링크를 받은 사람이 가입 -> 보호자 프로필 생성 후 토큰과 함께 호출
    @Transactional
    public AcceptInvitationResponse accept(Long accountId, AcceptInvitationRequest req) {
        GuardianInvitation inv = invitationRepository.findByToken(req.token())
                .filter(i -> i.getStatus() == InvitationStatus.PENDING)
                .orElseThrow(() -> new CustomException(GuardianErrorCode.INVITATION_NOT_FOUND));
        if (inv.isExpired()) {
            throw new CustomException(GuardianErrorCode.INVITATION_EXPIRED);
        }

        Profile guardian = profileRepository.findById(req.guardianProfileId())
                .filter(p -> p.getAccount().getId().equals(accountId))   // 내 계정의 프로필만
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));
        if (guardian.getRole() != ProfileRole.GUARDIAN) {
            throw new CustomException(GuardianErrorCode.NOT_GUARDIAN_PROFILE);
        }
        Profile senior = profileRepository.findById(inv.getSeniorProfileId())
                .orElseThrow(() -> new CustomException(GuardianErrorCode.INVITATION_NOT_FOUND));

        if (!guardianSeniorQueryRepository.existsByGuardianProfile_IdAndSeniorProfile_Id(
                guardian.getId(), senior.getId())) {
            guardianSeniorQueryRepository.save(new GuardianSenior(guardian, senior));
        }
        inv.accept();
        return new AcceptInvitationResponse(senior.getId());
    }

    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) return null;
        return phoneNumber.replaceAll("[^0-9]", "");
    }
}