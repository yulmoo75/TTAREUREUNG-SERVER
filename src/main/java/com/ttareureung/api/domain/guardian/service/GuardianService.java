package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.dto.PrimaryGuardianRequest;
import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianSeniorRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.security.AccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuardianService {

    private final AccessChecker accessChecker;
    private final SeniorAccessValidator seniorAccessValidator;
    private final GuardianSeniorRepository links;

    @Transactional
    public void changePrimary(Long accountId, PrimaryGuardianRequest req) {
        accessChecker.activeAccount(accountId);
        seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());

        List<GuardianSenior> seniorLinks = links.findAllByProfileId(req.seniorProfileId()).stream()
                .filter(l -> l.getStatus() == GuardianSeniorStatus.ACTIVE)
                .toList();

        if (seniorLinks.stream().noneMatch(l -> l.getGuardian().getId().equals(req.guardianId()))) {
            throw new CustomException(GuardianErrorCode.GUARDIAN_NOT_CONNECTED);
        }

        seniorLinks.forEach(l -> l.changePrimary(l.getGuardian().getId().equals(req.guardianId())));
    }
}