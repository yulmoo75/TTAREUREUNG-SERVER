package com.ttareureung.api.domain.profile.dto;

import com.ttareureung.api.domain.profile.entity.Gender;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProfileCreateRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        @Schema(example = "김순자", maxLength = 50)
        String name,

        @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
        @Schema(example = "1950-03-15", description = "선택 입력")
        LocalDate birthDate,

        @Schema(example = "FEMALE", description = "선택 입력")
        Gender gender,

        @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        @Schema(example = "010-1234-5678", description = "선택 입력")
        String phoneNumber,

        @Size(max = 20, message = "관계는 20자 이하여야 합니다.")
        @Schema(example = "어머니", description = "선택 입력", maxLength = 20)
        String relationship,

        @NotNull(message = "프로필 역할을 선택해주세요.")
        @Schema(example = "SENIOR")
        ProfileRole role,

        @Schema(example = "1", description = "시니어 생성 시에만 사용. 넣으면 해당 보호자와 바로 연결")
        Long guardianProfileId
) {
    public ProfileCreateRequest {
        name = name == null ? null : name.strip();
        // 선택 항목의 빈 문자열은 미입력 처리
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.strip();
        relationship = relationship == null || relationship.isBlank() ? null : relationship.strip();
    }
}
