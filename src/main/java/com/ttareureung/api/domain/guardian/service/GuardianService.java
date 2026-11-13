package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.dto.PrimaryGuardianRequest;
import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianSeniorQueryRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                .findGuardiansOfSenior(req.seniorProfileId(), ConnectionStatus.ACTIVE);
        if (links.stream().noneMatch(gs -> gs.getGuardianProfile().getId().equals(req.guardianId()))) {
            throw new BusinessException(GuardianErrorCode.GUARDIAN_NOT_CONNECTED);
        }
        // 대표는 시니어당 1명. 대상만 지정
        links.forEach(gs -> gs.changePrimary(gs.getGuardianProfile().getId().equals(req.guardianId())));
    }
}
