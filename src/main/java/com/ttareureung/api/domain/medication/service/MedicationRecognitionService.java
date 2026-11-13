package com.ttareureung.api.domain.medication.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ttareureung.api.domain.medication.client.AnthropicClient;
import com.ttareureung.api.domain.medication.dto.RecognitionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationRecognitionService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private static final String SYSTEM_PROMPT = """
            너는 처방전·약봉투·약 사진에서 복약 정보를 추출하는 도구다.
            - 이미지에 실제로 적힌 내용만 추출하고, 보이지 않는 값은 추측하지 말고 null로 둔다.
            - 약마다 항목 하나를 만든다.
            - meal_time_slots는 MORNING, LUNCH, DINNER 중에서만 고르고, 알 수 없으면 빈 배열로 둔다.
            - frequency는 "하루 3회, 식후 30분" 같은 한국어 문장으로 적는다.
            - confidence는 0~100 정수로, 글자를 얼마나 확실하게 읽었는지 적는다.
            - 약 정보가 전혀 없는 이미지면 {"medications": []} 를 반환한다.
            - 설명이나 마크다운 없이 JSON 객체만 출력한다.
            형식: {"medications":[{"drug_name":"","effect":"","frequency":"","meal_time_slots":[],"confidence":0}]}
            """;

    private final AnthropicClient anthropicClient;
    private final JsonMapper jsonMapper; // Boot 4는 Jackson 3: tools.jackson.databind.json.JsonMapper

    public RecognitionResponse recognize(MultipartFile file) {
        String mediaType = file.getContentType();
        if (file.isEmpty() || mediaType == null || !ALLOWED_TYPES.contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP, PDF 파일만 올릴 수 있습니다.");
        }

        String raw;
        try {
            raw = anthropicClient.askWithFile(SYSTEM_PROMPT, mediaType, file.getBytes(),
                    "이 파일에서 복약 정보를 추출해줘.");
        } catch (Exception e) {
            log.error("AI 인식 호출 실패", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 인식에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        RecognitionResponse result;
        try {
            // ```json 펜스 제거
            String json = raw.strip().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            result = jsonMapper.readValue(json, RecognitionResponse.class);
        } catch (Exception e) {
            log.error("AI 응답 파싱 실패: {}", raw, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 해석하지 못했습니다.");
        }

        if (result.medications() == null || result.medications().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "약 정보를 찾지 못했습니다. 직접 입력해주세요.");
        }
        return result;
    }
}
