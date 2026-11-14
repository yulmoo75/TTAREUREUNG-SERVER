package com.ttareureung.api.domain.guardian.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuardianSeniorQueryRepository extends JpaRepository<GuardianSenior, Long> {

    @Query("""
            select count(gs) > 0 from GuardianSenior gs
            where gs.senior.id = :seniorId
              and gs.guardian.account.id = :accountId
              and gs.status = :status
            """)
    boolean existsActiveLinkWithGuardianInAccount(@Param("seniorId") Long seniorId,
                                                  @Param("accountId") Long accountId,
                                                  @Param("status") GuardianSeniorStatus status);
}