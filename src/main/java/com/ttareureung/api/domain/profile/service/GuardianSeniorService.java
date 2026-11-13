package com.ttareureung.api.domain.profile.service;

import com.ttareureung.api.domain.profile.dto.GuardianSeniorCreateRequest;
import com.ttareureung.api.domain.profile.dto.GuardianSeniorResponse;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.GuardianSeniorRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

/**
 * 보호자-시니어 연결 생성·조회 로직.
 * 두 프로필 모두 토큰의 계정 소속인지 확인 후 연결
 */
@Service
@Transactional
public class GuardianSeniorService {

    private final ProfileAccessChecker accessChecker;
    private final GuardianSeniorRepository links;

    public GuardianSeniorService(ProfileAccessChecker accessChecker, GuardianSeniorRepository links) {
        this.accessChecker = accessChecker;
        this.links = links;
    }

    public GuardianSeniorResponse link(Long accountId, GuardianSeniorCreateRequest request) {
        accessChecker.activeAccount(accountId);
        Profile guardian = accessChecker.ownedProfile(accountId, request.guardianProfileId());
        Profile senior = accessChecker.ownedProfile(accountId, request.seniorProfileId());
        if (guardian.getRole() != ProfileRole.GUARDIAN || senior.getRole() != ProfileRole.SENIOR) {
            throw new CustomException(ProfileErrorCode.INVALID_LINK_ROLE);
        }
        if (links.existsByGuardianIdAndSeniorId(guardian.getId(), senior.getId())) {
            throw new CustomException(ProfileErrorCode.ALREADY_LINKED);
        }
        try {
            return GuardianSeniorResponse.from(links.saveAndFlush(new GuardianSenior(guardian, senior)));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 exists 확인을 통과한 경우 UNIQUE 제약(1062)에서 걸림
            if (e.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
                throw new CustomException(ProfileErrorCode.ALREADY_LINKED);
            }
            throw e;
        }
    }

    // 시니어의 마지막 연결 해제도 허용. 연결 없는 시니어는 프로필 목록에 남아 다시 연결 가능
    public void unlink(Long accountId, Long linkId) {
        accessChecker.activeAccount(accountId);
        GuardianSenior link = links.findByIdAndAccountId(linkId, accountId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.LINK_NOT_FOUND));
        links.delete(link);
    }

    // profileId가 없으면 계정 전체 연결, 있으면 해당 프로필이 포함된 연결만
    @Transactional(readOnly = true)
    public List<GuardianSeniorResponse> findAll(Long accountId, Long profileId) {
        List<GuardianSenior> result;
        if (profileId == null) {
            result = links.findAllInAccount(accountId);
        } else {
            accessChecker.ownedProfile(accountId, profileId);
            result = links.findAllByProfileId(profileId);
        }
        return result.stream().map(GuardianSeniorResponse::from).toList();
    }
}
