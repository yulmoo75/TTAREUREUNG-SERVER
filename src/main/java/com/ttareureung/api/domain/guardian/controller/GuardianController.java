package com.ttareureung.api.domain.guardian.controller;

import com.ttareureung.api.domain.guardian.dto.*;
import com.ttareureung.api.domain.guardian.service.GuardianInvitationService;
import com.ttareureung.api.domain.guardian.service.GuardianService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springfra


mework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guardians")
@Tag(name = "Guardian", description = "공동 보호자 관리")
public class GuardianController {

    private final GuardianService guardianService;
    private final GuardianInvitationService invitationService;

    public GuardianController(GuardianService guardianService, GuardianInvitationService invitationService) {
        this.guardianService = guardianService;
        this.invitationService = invitationService;
    }

    @Operation(summary = "대표 보호자 설정", description = "시니어의 대표 보호자를 지정합니다. 시니어당 1명만 대표가 됩니다.")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "접근 불가한 시니어 (PROFILE_002) 또는 연결되지 않은 보호자 (GUARDIAN_001)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PatchMapping(value = "/primary", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<Void>> changePrimary(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody PrimaryGuardianRequest request) {
        guardianService.changePrimary(accountId(jwt), request);
        return ResponseEntity.ok(BaseResponse.successWithoutData());
    }

    @Operation(summary = "공동 보호자 초대", description = "전화번호로 초대 문자를 보냅니다. 이미 연결됐거나 초대 대기 중인 번호는 건너뜁니다.")
    @ApiResponse(responseCode = "200", description = "초대 완료")
    @ApiResponse(responseCode = "400", description = "입력값 오류 또는 시니어 프로필이 아님 (MEDICATION_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "접근 불가한 시니어 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(value = "/invitation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<InvitationResponse>> invite(@AuthenticationPrincipal Jwt jwt,
                                                                   @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.ok(BaseResponse.success(invitationService.invite(accountId(jwt), request)));
    }

    @Operation(summary = "초대 수락", description = "초대 문자의 토큰으로 내 보호자 프로필을 시니어에 연결합니다.")
    @ApiResponse(responseCode = "200", description = "연결 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류 또는 보호자 프로필이 아님 (GUARDIAN_004)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없거나 이미 사용된 초대 (GUARDIAN_002), 내 프로필이 아님 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "410", description = "만료된 초대 (GUARDIAN_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(value = "/invitation/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<AcceptInvitationResponse>> accept(@AuthenticationPrincipal Jwt jwt,
                                                                         @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.ok(BaseResponse.success(invitationService.accept(accountId(jwt), request)));
    }

    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}