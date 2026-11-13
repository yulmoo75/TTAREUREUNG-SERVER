package com.ttareureung.api.domain.medication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AnthropicClient {

    private final RestClient restClient;
    private final String model;

    public AnthropicClient(@Value("${anthropic.api-key}") String apiKey,
                           @Value("${anthropic.model}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(factory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    // 이미지+프롬프트 -> 응답 반환
    public String askWithFile(String system, String mediaType, byte[] data, String prompt) {
        String blockType = "application/pdf".equals(mediaType) ? "document" : "image";
        Map<String, Object> source = Map.of(
                "type", "base64",
                "media_type", mediaType,
                "data", Base64.getEncoder().encodeToString(data));

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2000,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", blockType, "source", source),
                        Map.of("type", "text", "text", prompt)))));

        Response res = restClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Response.class);

        if (res == null || res.content() == null) {
            throw new IllegalStateException("Empty response from Anthropic");
        }
        return res.content().stream()
                .filter(c -> "text".equals(c.type()))
                .map(Content::text)
                .collect(Collectors.joining());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Content> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(String type, String text) {}
}
