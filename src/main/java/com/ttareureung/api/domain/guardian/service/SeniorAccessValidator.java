package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianSeniorQueryRepository;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeniorAccessValidator {

    private final ProfileRepository profileRepository;
    private final GuardianSeniorQueryRepository guardianSeniorQueryRepository;

    public Profile requireSenior(Long accountId, Long seniorProfileId) {
        Profile senior = profileRepository.findById(seniorProfileId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));

        // 권한 체크
        if (!hasAccess(accountId, seniorProfileId)) {
            throw new CustomException(GuardianErrorCode.UNAUTHORIZED_ACCESS);
        }

        return senior;
    }

    public boolean hasAccess(Long accountId, Long seniorProfileId) {
        // 권한 확인
        return true; // 임시
    }
}
