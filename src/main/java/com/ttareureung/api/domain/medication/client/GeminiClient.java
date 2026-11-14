package com.ttareureung.api.domain.medication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GeminiClient implements AiFileClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey, @Value("${gemini.model}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    @Override
    public String askWithFile(String system, String mediaType, byte[] data, String prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(
                        Map.of("inline_data", Map.of(
                                "mime_type", mediaType,
                                "data", Base64.getEncoder().encodeToString(data))),
                        Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0));

        Response res = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Response.class);

        String text = Optional.ofNullable(res)
                .map(Response::candidates)
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0).content())
                .map(Content::parts)
                .orElse(List.of()).stream()
                .map(Part::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        if (text.isBlank()) { // 텍스트가 없는 경우
            throw new IllegalStateException("Gemini 응답에 텍스트가 없습니다.");
        }
        return text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text) {}
}
