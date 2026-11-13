package com.ttareureung.api.domain.guardian.controller;

import com.ttareureung.api.domain.guardian.dto.*;
import com.ttareureung.api.domain.guardian.service.GuardianInvitationService;
import com.ttareureung.api.domain.guardian.service.GuardianService;
import com.ttareureung.api.global.common.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;
    private final GuardianInvitationService invitationService;

    @PatchMapping("/primary")
    public BaseResponse<Void> changePrimary(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody @Valid PrimaryGuardianRequest request) {
        guardianService.changePrimary(AuthPrincipals.accountId(jwt), request);
        return BaseResponse.successWithoutData();
    }

    @PostMapping("/invitation")
    public BaseResponse<InvitationResponse> invite(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody @Valid InvitationRequest request) {
        return BaseResponse.success(invitationService.invite(AuthPrincipals.accountId(jwt), request));
    }

    @PostMapping("/invitation/accept")
    public BaseResponse<AcceptInvitationResponse> accept(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestBody @Valid AcceptInvitationRequest request) {
        return BaseResponse.success(invitationService.accept(AuthPrincipals.accountId(jwt), request));
    }
}
