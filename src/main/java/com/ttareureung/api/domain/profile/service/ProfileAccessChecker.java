package com.ttareureung.api.domain.profile.service;

import com.ttareureung.api.domain.auth.exception.AuthErrorCode;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.domain.user.entity.AccountStatus;
import com.ttareureung.api.domain.user.repository.AccountRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 계정 활성 여부, 프로필 소유권 확인 공통 로직.
 * 프로필·연결·이후 복약 API에서 같은 기준으로 검증하기 위해 분리
 */
@Component
public class ProfileAccessChecker {

    private final AccountRepository accounts;
    private final ProfileRepository profiles;

    public ProfileAccessChecker(AccountRepository accounts, ProfileRepository profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    // Access Token 유효 시간(최대 30분) 중 비활성화된 계정의 변경 차단용 재확인
    public Account activeAccount(Long accountId) {
        return requireActive(accounts.findById(accountId));
    }

    // 계정 단위 규칙(마지막 보호자 삭제 금지 등)을 확인하는 변경 작업용. 트랜잭션 종료까지 계정 행 잠금
    public Account lockedActiveAccount(Long accountId) {
        return requireActive(accounts.findByIdForUpdate(accountId));
    }

    // profileId만으로 조회하면 다른 계정 프로필 접근 가능. accountId 조건 필수
    public Profile ownedProfile(Long accountId, Long profileId) {
        return profiles.findByIdAndAccountId(profileId, accountId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }

    private static Account requireActive(Optional<Account> found) {
        Account account = found.orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.ACCOUNT_INACTIVE);
        }
        return account;
    }
}
