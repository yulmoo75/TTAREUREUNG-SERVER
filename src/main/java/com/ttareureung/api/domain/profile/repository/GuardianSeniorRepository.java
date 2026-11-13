package com.ttareureung.api.domain.profile.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// 응답에 양쪽 프로필 이름이 필요해서 fetch join으로 한 번에 조회 (N+1 방지)
public interface GuardianSeniorRepository extends JpaRepository<GuardianSenior, Long> {

    @Query("""
            select gs from GuardianSenior gs
            join fetch gs.guardian join fetch gs.senior
            where gs.guardian.account.id = :accountId
            order by gs.id
            """)
    List<GuardianSenior> findAllInAccount(Long accountId);

    @Query("""
            select gs from GuardianSenior gs
            join fetch gs.guardian join fetch gs.senior
            where gs.guardian.id = :profileId or gs.senior.id = :profileId
            order by gs.id
            """)
    List<GuardianSenior> findAllByProfileId(Long profileId);

    boolean existsByGuardianIdAndSeniorId(Long guardianId, Long seniorId);

    // 보호자 쪽 계정 기준으로 소유권 확인 (연결 생성 시 두 프로필 계정 일치 검증 완료)
    @Query("select gs from GuardianSenior gs where gs.id = :id and gs.guardian.account.id = :accountId")
    Optional<GuardianSenior> findByIdAndAccountId(Long id, Long accountId);

    @Modifying
    @Query("delete from GuardianSenior gs where gs.guardian.id = :profileId or gs.senior.id = :profileId")
    int deleteAllByProfileId(Long profileId);
}
