package com.ttareureung.api.domain.medication;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicationScheduleApiTests extends ApiTestSupport {

    private static final String D = "2026-11-04";

    @Test
    void returnsTimelineGroupedBySlotInMealOrder() throws Exception {
        Family f = newFamily();
        registerMedication(f.accountId(), f.seniorId(), "혈압약", D, "2026-11-05", "LUNCH", "MORNING");

        getAs(bearer(f.accountId()), scheduleUrl(f.seniorId(), D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.date").value(D))
                .andExpect(jsonPath("$.data.time_slots.length()").value(2))
                .andExpect(jsonPath("$.data.time_slots[0].slot").value("MORNING"))
                .andExpect(jsonPath("$.data.time_slots[0].slot_name").value("아침 식후"))
                .andExpect(jsonPath("$.data.time_slots[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.time_slots[0].medicines[0].name").value("혈압약"))
                .andExpect(jsonPath("$.data.time_slots[0].medicines[0].log_id").isNumber())
                .andExpect(jsonPath("$.data.time_slots[1].slot").value("LUNCH"));
    }

    @Test
    void emptyTimelineWhenNothingScheduledThatDay() throws Exception {
        Family f = newFamily();
        registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");

        getAs(bearer(f.accountId()), scheduleUrl(f.seniorId(), "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.time_slots.length()").value(0));
    }

    @Test
    void statusChangeUpdatesTakenAtAndSlotSummary() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        long med1 = registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");
        long med2 = registerMedication(f.accountId(), f.seniorId(), "소화제", D, D, "MORNING");
        long log1 = logId(med1, "MORNING", D);
        long log2 = logId(med2, "MORNING", D);

        patchStatus(token, log1, "TAKEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.log_id").value(log1))
                .andExpect(jsonPath("$.data.status").value("TAKEN"));
        assertThat(jdbcTemplate.queryForMap("SELECT status, taken_at FROM medication_logs WHERE id = ?", log1)
                .get("taken_at")).isNotNull();
        assertSlotStatus(token, f.seniorId(), "PARTIAL");

        patchStatus(token, log2, "TAKEN").andExpect(status().isOk());
        assertSlotStatus(token, f.seniorId(), "TAKEN");

        patchStatus(token, log1, "MISSED").andExpect(status().isOk());
        patchStatus(token, log2, "MISSED").andExpect(status().isOk());
        assertSlotStatus(token, f.seniorId(), "MISSED");
        assertThat(jdbcTemplate.queryForMap("SELECT status, taken_at FROM medication_logs WHERE id = ?", log1)
                .get("taken_at")).isNull();
    }

    @ParameterizedTest(name = "invalid status body {index}")
    @ValueSource(strings = {"{}", "{\"status\":\"DONE\"}", "{\"status\":null}"})
    void rejectsInvalidStatusBody(String body) throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");

        patchJson(bearer(f.accountId()), "/api/medications/logs/" + logId(medicationId, "MORNING", D), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void unknownLogReturnsNotFound() throws Exception {
        patchStatus(bearer(newAccount()), Long.MAX_VALUE, "TAKEN")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_002"));
    }

    @Test
    void otherAccountCannotReadOrChangeTimeline() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");
        long logId = logId(medicationId, "MORNING", D);
        String stranger = bearer(newAccount());

        getAs(stranger, scheduleUrl(f.seniorId(), D))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        patchStatus(stranger, logId, "TAKEN")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_002"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM medication_logs WHERE id = ?", String.class, logId))
                .isEqualTo("PENDING");
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(get(scheduleUrl(1, D)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
        mockMvc.perform(patch("/api/medications/logs/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TAKEN\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions patchStatus(String token, long logId, String status) throws Exception {
        return patchJson(token, "/api/medications/logs/" + logId, Map.of("status", status));
    }

    private void assertSlotStatus(String token, long seniorId, String expected) throws Exception {
        getAs(token, scheduleUrl(seniorId, D))
                .andExpect(jsonPath("$.data.time_slots[0].status").value(expected));
    }
}
