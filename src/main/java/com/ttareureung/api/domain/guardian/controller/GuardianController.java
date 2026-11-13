package com.ttareureung.api.domain.guardian.controller;

import com.ttareureung.api.domain.guardian.dto.*;
import com.ttareureung.api.domain.guardian.service.GuardianInvitationService;
import com.ttareureung.api.domain.guardian.service.GuardianService;
import com.ttareureung.api.global.common.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;
    private final GuardianInvitationService invitationService;

    @PatchMapping("/primary")
    public BaseResponse<Void> changePrimary(@RequestParam Long accountId, // 임시 파라미터
                                            @RequestBody @Valid PrimaryGuardianRequest request) {
        guardianService.changePrimary(accountId, request);
        return BaseResponse.successWithoutData();
    }

    @PostMapping("/invitation")
    public BaseResponse<InvitationResponse> invite(@RequestParam Long accountId, // 임시 파라미터
                                                   @RequestBody @Valid InvitationRequest request) {
        return BaseResponse.success(invitationService.invite(accountId, request));
    }

    @PostMapping("/invitation/accept")
    public BaseResponse<AcceptInvitationResponse> accept(@RequestParam Long accountId, // 임시 파라미터
                                                         @RequestBody @Valid AcceptInvitationRequest request) {
        return BaseResponse.success(invitationService.accept(accountId, request));
    }
}