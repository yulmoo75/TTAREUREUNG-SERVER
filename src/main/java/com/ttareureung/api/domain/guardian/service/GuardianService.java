package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.dto.PrimaryGuardianRequest;
import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianSeniorQueryRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuardianService {

    private final GuardianSeniorQueryRepository guardianSeniorQueryRepository;
    private final SeniorAccessValidator seniorAccessValidator;

    @Transactional
    public void changePrimary(Long accountId, PrimaryGuardianRequest req) {
        seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());

        List<GuardianSenior> links = guardianSeniorQueryRepository
                .findGuardiansOfSenior(req.seniorProfileId(), GuardianSeniorStatus.ACTIVE);

        if (links.stream().noneMatch(gs -> gs.getGuardian().getId().equals(req.guardianId()))) {
            throw new CustomException(GuardianErrorCode.GUARDIAN_NOT_CONNECTED);
        }

        // 대표는 시니어당 1명. 대상만 지정
        links.forEach(gs -> gs.changePrimary(gs.getGuardian().getId().equals(req.guardianId())));
    }
}