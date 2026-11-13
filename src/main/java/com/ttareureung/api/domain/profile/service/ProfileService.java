package com.ttareureung.api.domain.profile.service;

import com.ttareureung.api.domain.profile.dto.ProfileCreateRequest;
import com.ttareureung.api.domain.profile.dto.ProfileResponse;
import com.ttareureung.api.domain.profile.dto.ProfileSummary;
import com.ttareureung.api.domain.profile.dto.ProfileUpdateRequest;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.GuardianSeniorRepository;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가족 계정의 프로필 생성·조회·수정 로직.
 * accountId는 Access Token 기준이라 요청 본문으로 다른 계정 지정 불가
 */
@Service
@Transactional
public class ProfileService {

    private final ProfileAccessChecker accessChecker;
    private final ProfileRepository profiles;
    private final GuardianSeniorRepository links;

    public ProfileService(ProfileAccessChecker accessChecker, ProfileRepository profiles, GuardianSeniorRepository links) {
        this.accessChecker = accessChecker;
        this.profiles = profiles;
        this.links = links;
    }

    public ProfileResponse create(Long accountId, ProfileCreateRequest request) {
        // 마지막 보호자 삭제와 동시에 실행되면 보호자 없는 시니어가 생길 수 있어 계정 잠금
        Account account = accessChecker.lockedActiveAccount(accountId);
        // 가입 흐름이 보호자 → 시니어 순서라 보호자 없는 시니어 생성 차단
        if (request.role() == ProfileRole.SENIOR && !profiles.existsByAccountIdAndRole(accountId, ProfileRole.GUARDIAN)) {
            throw new CustomException(ProfileErrorCode.GUARDIAN_REQUIRED);
        }
        Profile guardian = request.guardianProfileId() == null ? null : linkableGuardian(accountId, request);

        Profile profile = profiles.save(new Profile(account, request.name(), request.birthDate(), request.gender(),
                request.phoneNumber(), request.relationship(), request.role()));
        // 시니어 생성과 연결을 한 트랜잭션으로 처리. 연결 실패 시 시니어도 롤백되어 담당자 없는 시니어 방지
        if (guardian == null) {
            return ProfileResponse.of(profile, List.of());
        }
        links.save(new GuardianSenior(guardian, profile));
        return ProfileResponse.of(profile, List.of(ProfileSummary.from(guardian)));
    }

    // 조회 전용이라 readOnly로 변경 감지 비용 절감
    @Transactional(readOnly = true)
    public List<ProfileResponse> findAll(Long accountId) {
        // 프로필마다 연결을 조회하지 않고 계정 전체 연결을 한 번에 가져와서 분배
        List<GuardianSenior> accountLinks = links.findAllInAccount(accountId);
        return profiles.findAllByAccountIdOrderByIdAsc(accountId).stream()
                .map(profile -> ProfileResponse.of(profile, linkedProfiles(profile, accountLinks)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse findOne(Long accountId, Long profileId) {
        return withLinks(accessChecker.ownedProfile(accountId, profileId));
    }

    // save 호출 없이 트랜잭션 종료 시 변경 감지로 UPDATE
    public ProfileResponse update(Long accountId, Long profileId, ProfileUpdateRequest request) {
        accessChecker.activeAccount(accountId);
        Profile profile = accessChecker.ownedProfile(accountId, profileId);
        profile.update(
                keepIfNull(request.name(), profile.getName()),
                keepIfNull(request.birthDate(), profile.getBirthDate()),
                keepIfNull(request.gender(), profile.getGender()),
                clearIfEmpty(request.phoneNumber(), profile.getPhoneNumber()),
                clearIfEmpty(request.relationship(), profile.getRelationship()));
        return withLinks(profile);
    }

    // 연결은 함께 삭제. 복약 데이터 처리는 medications 테이블 생성 후 팀원과 정한 기준으로 추가
    public void delete(Long accountId, Long profileId) {
        // 보호자 두 명 동시 삭제 시 둘 다 "보호자 2명"으로 확인하고 지우는 경쟁 방지
        accessChecker.lockedActiveAccount(accountId);
        Profile profile = accessChecker.ownedProfile(accountId, profileId);
        if (profile.getRole() == ProfileRole.GUARDIAN
                && profiles.countByAccountIdAndRole(accountId, ProfileRole.GUARDIAN) == 1
                && profiles.existsByAccountIdAndRole(accountId, ProfileRole.SENIOR)) {
            throw new CustomException(ProfileErrorCode.LAST_GUARDIAN);
        }
        links.deleteAllByProfileId(profileId);
        profiles.delete(profile);
    }

    // 보호자 프로필 생성에 guardianProfileId를 넣는 경우도 잘못된 요청으로 처리
    private Profile linkableGuardian(Long accountId, ProfileCreateRequest request) {
        Profile guardian = accessChecker.ownedProfile(accountId, request.guardianProfileId());
        if (request.role() != ProfileRole.SENIOR || guardian.getRole() != ProfileRole.GUARDIAN) {
            throw new CustomException(ProfileErrorCode.INVALID_LINK_ROLE);
        }
        return guardian;
    }

    private ProfileResponse withLinks(Profile profile) {
        return ProfileResponse.of(profile, linkedProfiles(profile, links.findAllByProfileId(profile.getId())));
    }

    private static List<ProfileSummary> linkedProfiles(Profile profile, List<GuardianSenior> candidates) {
        return candidates.stream()
                .filter(link -> link.involves(profile.getId()))
                .map(link -> ProfileSummary.from(link.counterpartOf(profile.getId())))
                .toList();
    }

    private static <T> T keepIfNull(T requested, T current) {
        return requested == null ? current : requested;
    }

    private static String clearIfEmpty(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return requested.isEmpty() ? null : requested;
    }
}
