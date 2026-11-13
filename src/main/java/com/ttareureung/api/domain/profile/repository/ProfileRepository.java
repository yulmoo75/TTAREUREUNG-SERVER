package com.ttareureung.api.domain.profile.repository;

import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 다른 가족 계정 프로필이 섞이지 않도록 모든 조회에 accountId 조건 포함
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    List<Profile> findAllByAccountIdOrderByIdAsc(Long accountId);

    boolean existsByAccountIdAndRole(Long accountId, ProfileRole role);

    long countByAccountIdAndRole(Long accountId, ProfileRole role);

    Optional<Profile> findByIdAndAccountId(Long id, Long accountId);
}
