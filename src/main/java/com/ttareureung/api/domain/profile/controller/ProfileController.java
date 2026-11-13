package com.ttareureung.api.domain.profile.controller;

import com.ttareureung.api.domain.profile.dto.ProfileCreateRequest;
import com.ttareureung.api.domain.profile.dto.ProfileResponse;
import com.ttareureung.api.domain.profile.dto.ProfileUpdateRequest;
import com.ttareureung.api.domain.profile.service.ProfileService;
import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인한 가족 계정의 프로필 API.
 * 토큰 검증은 Spring Security 담당, 여기서는 검증된 subject(accountId)만 사용
 */
@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profile", description = "가족 계정 프로필")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "프로필 생성", description = "로그인한 계정에 보호자 또는 시니어 프로필을 추가합니다. 회원가입 후 첫 보호자 등록과 시니어 등록 모두 이 API를 사용하며, 시니어는 보호자 프로필이 있어야 만들 수 있습니다. 시니어 생성 시 guardianProfileId를 넣으면 그 보호자와 바로 연결됩니다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류 또는 guardianProfileId 역할 불일치 (PROFILE_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "guardianProfileId가 없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "보호자 프로필 없이 시니어 생성 (PROFILE_001)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<ProfileResponse>> create(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody ProfileCreateRequest request) {
        ProfileResponse response = profileService.create(accountId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
    }

    @Operation(summary = "프로필 목록 조회", description = "로그인한 계정의 프로필을 생성 순서대로 반환합니다. 프로필 선택 화면에서 사용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping
    public ResponseEntity<BaseResponse<List<ProfileResponse>>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(BaseResponse.success(profileService.findAll(accountId(jwt))));
    }

    @Operation(summary = "프로필 상세 조회", description = "로그인한 계정의 프로필 하나를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "profileId 형식 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @GetMapping("/{profileId}")
    public ResponseEntity<BaseResponse<ProfileResponse>> findOne(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable Long profileId) {
        return ResponseEntity.ok(BaseResponse.success(profileService.findOne(accountId(jwt), profileId)));
    }

    @Operation(summary = "프로필 수정", description = "보낸 필드만 수정합니다. 전화번호와 관계는 빈 문자열을 보내면 삭제됩니다. role은 수정할 수 없습니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "입력값 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PatchMapping(value = "/{profileId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<ProfileResponse>> update(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable Long profileId,
                                                                @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(profileService.update(accountId(jwt), profileId, request)));
    }

    @Operation(summary = "프로필 삭제", description = "프로필과 그 프로필의 보호자-시니어 연결을 함께 삭제합니다. 시니어가 있는 계정의 마지막 보호자는 삭제할 수 없습니다.")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "400", description = "profileId 형식 오류", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 프로필 또는 다른 계정의 프로필 (PROFILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "시니어가 있는 계정의 마지막 보호자 (PROFILE_006)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @DeleteMapping("/{profileId}")
    public ResponseEntity<BaseResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long profileId) {
        profileService.delete(accountId(jwt), profileId);
        return ResponseEntity.ok(BaseResponse.successWithoutData());
    }

    // subject 숫자 여부는 JwtConfig에서 검증 완료
    private Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
