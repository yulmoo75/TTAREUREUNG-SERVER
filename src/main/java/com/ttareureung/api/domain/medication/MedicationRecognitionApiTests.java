package com.ttareureung.api.domain.medication;

import com.ttareureung.api.domain.ai.AnthropicClient;
import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicationRecognitionApiTests extends ApiTestSupport {

    private static final String RESULT = """
            {"medications":[{"drug_name":"아로르신정 25밀리그램","effect":"관절염 통증 완화",
            "frequency":"1일 2회, 아침/저녁 식후 복용","meal_time_slots":["MORNING","DINNER"],"confidence":99}]}""";

    @MockitoBean
    private AiFileClient aiFileClient;

    @Test
    void returnsRecognizedMedications() throws Exception {
        when(anthropicClient.askWithFile(any(), any(), any(), any())).thenReturn(RESULT);

        upload(bearer(newAccount()), "image/jpeg", new byte[]{1, 2, 3})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.medications.length()").value(1))
                .andExpect(jsonPath("$.data.medications[0].drug_name").value("아로르신정 25밀리그램"))
                .andExpect(jsonPath("$.data.medications[0].meal_time_slots[1]").value("DINNER"))
                .andExpect(jsonPath("$.data.medications[0].confidence").value(99));
        verify(anthropicClient).askWithFile(any(), eq("image/jpeg"), any(), any());
    }

    @Test
    void stripsMarkdownFenceAroundJson() throws Exception {
        when(anthropicClient.askWithFile(any(), any(), any(), any()))
                .thenReturn("```json\n" + RESULT + "\n```");

        upload(bearer(newAccount()), "application/pdf", new byte[]{1})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medications[0].drug_name").value("아로르신정 25밀리그램"));
    }

    @Test
    void emptyResultReturnsUnprocessable() throws Exception {
        when(anthropicClient.askWithFile(any(), any(), any(), any())).thenReturn("{\"medications\":[]}");

        upload(bearer(newAccount()), "image/png", new byte[]{1})
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEDICATION_008"));
    }

    @Test
    void unparsableAiResponseReturnsBadGateway() throws Exception {
        when(anthropicClient.askWithFile(any(), any(), any(), any())).thenReturn("죄송합니다. 읽을 수 없습니다.");

        upload(bearer(newAccount()), "image/jpeg", new byte[]{1})
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MEDICATION_010"));
    }

    @Test
    void aiCallFailureReturnsBadGateway() throws Exception {
        when(anthropicClient.askWithFile(any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        upload(bearer(newAccount()), "image/jpeg", new byte[]{1})
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MEDICATION_009"));
    }

    @Test
    void rejectsUnsupportedOrEmptyFileWithoutCallingAi() throws Exception {
        String token = bearer(newAccount());

        upload(token, "text/plain", new byte[]{1})
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDICATION_007"));
        upload(token, "image/jpeg", new byte[0])
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(multipart("/api/medications/recognition")
                        .file(new MockMultipartFile("file", "rx", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(String token, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/medications/recognition")
                .file(new MockMultipartFile("file", "rx", contentType, content))
                .header(HttpHeaders.AUTHORIZATION, token));
    }
}
