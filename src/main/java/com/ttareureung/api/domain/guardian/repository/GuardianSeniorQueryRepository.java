package com.ttareureung.api.domain.guardian.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuardianSeniorQueryRepository extends JpaRepository<GuardianSenior, Long> {

    List<GuardianSenior> findGuardiansOfSenior(Long seniorProfileId, GuardianSeniorStatus status);

    boolean existsByGuardianProfile_IdAndSeniorProfile_Id(Long guardianProfileId, Long seniorProfileId);
}
