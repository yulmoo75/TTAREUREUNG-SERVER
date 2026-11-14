package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.repository.GuardianSeniorQueryRepository;
import com.ttareureung.api.domain.medication.exception.MedicationErrorCode;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SeniorAccessValidator {

    private final ProfileRepository profiles;
    private final GuardianSeniorQueryRepository links;

    public SeniorAccessValidator(ProfileRepository profiles, GuardianSeniorQueryRepository links) {
        this.profiles = profiles;
        this.links = links;
    }

    /** 접근 가능한 시니어를 반환. 없거나 권한이 없으면 똑같이 PROFILE_NOT_FOUND  */
    public Profile requireSenior(Long accountId, Long seniorProfileId) {
        Profile profile = findAccessible(accountId, seniorProfileId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));
        if (profile.getRole() != ProfileRole.SENIOR) {
            throw new CustomException(MedicationErrorCode.NOT_SENIOR_PROFILE);
        }
        return profile;
    }

    public boolean hasAccess(Long accountId, Long seniorProfileId) {
        return findAccessible(accountId, seniorProfileId)
                .filter(p -> p.getRole() == ProfileRole.SENIOR)
                .isPresent();
    }

    private Optional<Profile> findAccessible(Long accountId, Long profileId) {
        return profiles.findByIdAndAccountId(profileId, accountId)
                .or(() -> profiles.findById(profileId).filter(p ->
                        links.existsActiveLinkWithGuardianInAccount(p.getId(), accountId, GuardianSeniorStatus.ACTIVE)));
    }
}