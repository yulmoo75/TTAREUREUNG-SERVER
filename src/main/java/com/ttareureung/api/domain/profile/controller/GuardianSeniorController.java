package com.ttareureung.api.domain.profile.controller;

import com.ttareureung.api.domain.profile.dto.GuardianSeniorCreateRequest;
import com.ttareureung.api.domain.profile.dto.GuardianSeniorResponse;
import com.ttareureung.api.domain.profile.service.GuardianSeniorService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 보호자-시니어 연결 API.
 * 토큰 검증은 Spring Security 담당, 여기서는 검증된 subject(accountId)만 사용
 */
@RestController
@RequestMapping("/api/v1/guardian-seniors")
@Tag(name = "GuardianSenior", description = "보호자-시니어 연결")
public class GuardianSeniorController {

    private final GuardianSeniorService guardianSeniorService;

    public GuardianSeniorController(GuardianSeniorService guardianSeniorService) {
        this.guardianSeniorService = guardianSeniorService;
    }

    @Operation(summary = "보호자-시니어 연결", description = "같은 계정의 보호자 프로필과 시니어 프로필을 연결합니다.")
    @ApiResponse(responseCode = "201", description = "연결 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류 또는 보호자·시니어 역할 불일치 (PROFILE_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 연결됨 (PROFILE_004)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<GuardianSeniorResponse>> link(@AuthenticationPrincipal Jwt jwt,
                                                                     @Valid @RequestBody GuardianSeniorCreateRequest request) {
        GuardianSeniorResponse response = guardianSeniorService.link(accountId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
    }

    @Operation(summary = "연결 해제", description = "보호자-시니어 연결을 삭제합니다. 프로필은 그대로 남습니다.")
    @ApiResponse(responseCode = "200", description = "해제 성공")
    @ApiResponse(responseCode = "400", description = "linkId 형식 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 연결 또는 다른 계정의 연결 (PROFILE_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @DeleteMapping("/{linkId}")
    public ResponseEntity<BaseResponse<Void>> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable Long linkId) {
        guardianSeniorService.unlink(accountId(jwt), linkId);
        return ResponseEntity.ok(BaseResponse.successWithoutData());
    }

    @Operation(summary = "연결 목록 조회", description = "profileId를 넣으면 그 프로필의 연결만, 없으면 계정 전체 연결을 반환합니다. 보호자 화면의 담당 시니어 목록에 사용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "profileId 형식 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping
    public ResponseEntity<BaseResponse<List<GuardianSeniorResponse>>> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "기준 프로필 ID (선택)") @RequestParam(required = false) Long profileId) {
        return ResponseEntity.ok(BaseResponse.success(guardianSeniorService.findAll(accountId(jwt), profileId)));
    }

    // subject 숫자 여부는 JwtConfig에서 검증 완료
    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
