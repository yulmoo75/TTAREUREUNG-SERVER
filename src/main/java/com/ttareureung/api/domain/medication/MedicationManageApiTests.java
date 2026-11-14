package com.ttareureung.api.domain.medication;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicationManageApiTests extends ApiTestSupport {

    private static final String D1 = "2026-11-04";
    private static final String OK_FIELDS = "\"senior_profile_id\":1,\"drug_name\":\"약\",\"start_date\":\"2026-11-04\","
            + "\"end_date\":\"2026-11-05\",\"cycle_type\":\"DAILY\",\"frequency\":\"하루 1회\",\"notify_on_miss\":true";

    @Test
    void registersMedicationAndCreatesLogsPerDayAndSlot() throws Exception {
        Family f = newFamily();
        var body = medicationItem("아미노부프로펜", D1, "2026-11-06", "MORNING", "MORNING", "LUNCH");
        body.put("custom_drug_name", "허리 통증 줄여주는 약");

        MvcResult result = registerAs(bearer(f.accountId()), f.seniorId(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.medication_id").isNumber())
                .andReturn();
        long medicationId = idOf(result, "medication_id");

        var row = jdbcTemplate.queryForMap("SELECT * FROM medications WHERE id = ?", medicationId);
        assertThat(((Number) row.get("senior_profile_id")).longValue()).isEqualTo(f.seniorId());
        assertThat(row.get("custom_drug_name")).isEqualTo("허리 통증 줄여주는 약");
        assertThat(row.get("cycle_type")).isEqualTo("DAILY");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", medicationId)).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ? AND status = 'PENDING' AND taken_at IS NULL",
                medicationId)).isEqualTo(6);
    }

    @Test
    void acceptsOneYearPeriodButRejectsLonger() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        registerAs(token, f.seniorId(), medicationItem("약", "2026-01-01", "2027-01-02", "MORNING"))
                .andExpect(status().isCreated());
        assertThat(count("""
                SELECT COUNT(*) FROM medication_logs ml JOIN medications m ON ml.medication_id = m.id
                WHERE m.senior_profile_id = ?""", f.seniorId())).isEqualTo(367);

        registerAs(token, f.seniorId(), medicationItem("약", "2026-01-01", "2027-01-03", "MORNING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_005"));
    }

    @Test
    void rejectsEndDateBeforeStartDate() throws Exception {
        Family f = newFamily();

        registerAs(bearer(f.accountId()), f.seniorId(), medicationItem("약", "2026-11-06", "2026-11-04", "MORNING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_004"));
        assertThat(count("SELECT COUNT(*) FROM medications WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void rejectsGuardianProfileAndOtherAccountsSenior() throws Exception {
        Family mine = newFamily();
        Family other = newFamily();
        String token = bearer(mine.accountId());

        registerAs(token, mine.guardianId(), medicationItem("약", D1, D1, "MORNING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_003"));
        registerAs(token, other.seniorId(), medicationItem("약", D1, D1, "MORNING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        assertThat(count("SELECT COUNT(*) FROM medications WHERE senior_profile_id IN (?, ?, ?)",
                mine.guardianId(), mine.seniorId(), other.seniorId())).isZero();
    }

    @ParameterizedTest(name = "invalid register body {index}")
    @ValueSource(strings = {
            "{}",
            "{\"senior_profile_id\":1}",
            "{" + OK_FIELDS + "}",
            "{" + OK_FIELDS + ",\"meal_time_slots\":[]}",
            "{" + OK_FIELDS + ",\"meal_time_slots\":[\"BRUNCH\"]}"
    })
    void rejectsInvalidRegisterBody(String body) throws Exception {
        postJson(bearer(newAccount()), "/api/medications", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void listsOnlyAccessibleSeniorsMedications() throws Exception {
        Family mine = newFamily();
        Family other = newFamily();
        registerMedication(mine.accountId(), mine.seniorId(), "약A", D1, D1, "MORNING");
        registerMedication(mine.accountId(), mine.seniorId(), "약B", D1, D1, "LUNCH");
        registerMedication(other.accountId(), other.seniorId(), "남의 약", D1, D1, "MORNING");
        String token = bearer(mine.accountId());

        getAs(token, "/api/medications?senior_id=" + mine.seniorId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medications.length()").value(2))
                .andExpect(jsonPath("$.data.medications[0].medication_id").isNumber());
        getAs(token, "/api/medications?senior_id=" + other.seniorId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
    }

    @Test
    void updatesOnlySentFields() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "아미노부프로펜", D1, "2026-11-05", "MORNING");
        String token = bearer(f.accountId());
        String url = "/api/medications/" + medicationId;

        putJson(token, url, Map.of("custom_drug_name", "허리 아플 때 먹는 약", "frequency", "하루 2회, 식후 30분"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medication_id").value(medicationId));
        var row = jdbcTemplate.queryForMap("SELECT * FROM medications WHERE id = ?", medicationId);
        assertThat(row.get("custom_drug_name")).isEqualTo("허리 아플 때 먹는 약");
        assertThat(row.get("frequency")).isEqualTo("하루 2회, 식후 30분");
        assertThat(row.get("drug_name")).isEqualTo("아미노부프로펜");
        assertThat(row.get("effect")).isEqualTo("통증 완화");

        putJson(token, url, "{}").andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT custom_drug_name FROM medications WHERE id = ?", String.class, medicationId))
                .isEqualTo("허리 아플 때 먹는 약");

        putJson(token, url, Map.of("frequency", "가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void otherAccountCannotUpdateOrDeleteMedication() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D1, D1, "MORNING");
        String stranger = bearer(newAccount());

        putJson(stranger, "/api/medications/" + medicationId, Map.of("frequency", "바뀜"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_001"));
        deleteAs(stranger, "/api/medications/" + medicationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_001"));
        assertThat(jdbcTemplate.queryForObject("SELECT frequency FROM medications WHERE id = ?", String.class, medicationId))
                .isEqualTo("하루 2회, 식후 30분");
    }

    @Test
    void deletesMedicationTogetherWithItsLogs() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D1, "2026-11-06", "MORNING", "LUNCH");

        deleteAs(bearer(f.accountId()), "/api/medications/" + medicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(count("SELECT COUNT(*) FROM medications WHERE id = ?", medicationId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", medicationId)).isZero();
    }

    @Test
    void registersSeveralMedicationsAtOnce() throws Exception {
        Family f = newFamily();
        var body = new LinkedHashMap<String, Object>();
        body.put("senior_profile_id", f.seniorId());
        body.put("medications", List.of(
                medicationItem("약A", D1, D1, "MORNING"),
                medicationItem("약B", D1, "2026-11-05", "MORNING", "DINNER")));

        postJson(bearer(f.accountId()), "/api/medications/bulk", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.medication_ids.length()").value(2));

        assertThat(count("SELECT COUNT(*) FROM medications WHERE senior_profile_id = ?", f.seniorId())).isEqualTo(2);
        assertThat(count("""
                SELECT COUNT(*) FROM medication_logs ml JOIN medications m ON ml.medication_id = m.id
                WHERE m.senior_profile_id = ?""", f.seniorId())).isEqualTo(5);
    }

    @Test
    void bulkRegisterRollsBackEverythingWhenOneItemIsInvalid() throws Exception {
        Family f = newFamily();
        var body = new LinkedHashMap<String, Object>();
        body.put("senior_profile_id", f.seniorId());
        body.put("medications", List.of(
                medicationItem("정상", D1, D1, "MORNING"),
                medicationItem("기간 오류", "2026-11-06", "2026-11-04", "MORNING")));

        postJson(bearer(f.accountId()), "/api/medications/bulk", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_004"));

        assertThat(count("SELECT COUNT(*) FROM medications WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void bulkRegisterRejectsEmptyOrTooManyItems() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        postJson(bearer(f.accountId()), "/api/medications/bulk",
                Map.of("senior_profile_id", f.seniorId(), "medications", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        postJson(token, "/api/medications/bulk",
                Map.of("senior_profile_id", f.seniorId(),
                        "medications", Collections.nCopies(21, medicationItem("약", D1, D1, "MORNING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        assertThat(count("SELECT COUNT(*) FROM medications WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void deletesSeveralMedicationsAtOnce() throws Exception {
        Family f = newFamily();
        long m1 = registerMedication(f.accountId(), f.seniorId(), "약A", D1, "2026-11-05", "MORNING");
        long m2 = registerMedication(f.accountId(), f.seniorId(), "약B", D1, D1, "LUNCH");
        long m3 = registerMedication(f.accountId(), f.seniorId(), "약C", D1, D1, "DINNER");

        deleteAs(bearer(f.accountId()), "/api/medications?medication_ids=" + m1 + "," + m2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted_count").value(2));

        assertThat(count("SELECT COUNT(*) FROM medications WHERE id IN (?, ?)", m1, m2)).isZero();
        assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id IN (?, ?)", m1, m2)).isZero();
        assertThat(count("SELECT COUNT(*) FROM medications WHERE id = ?", m3)).isEqualTo(1);
    }

    @Test
    void bulkDeleteIsAllOrNothingWhenAnyIdIsNotAccessible() throws Exception {
        Family mine = newFamily();
        Family other = newFamily();
        long myMed = registerMedication(mine.accountId(), mine.seniorId(), "내 약", D1, D1, "MORNING");
        long otherMed = registerMedication(other.accountId(), other.seniorId(), "남의 약", D1, D1, "MORNING");

        deleteAs(bearer(mine.accountId()), "/api/medications?medication_ids=" + myMed + "," + otherMed)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_001"));
        assertThat(count("SELECT COUNT(*) FROM medications WHERE id IN (?, ?)", myMed, otherMed)).isEqualTo(2);

        deleteAs(bearer(mine.accountId()), "/api/medications").andExpect(status().isBadRequest());
    }

    @Test
    void deletingSeniorProfileAlsoRemovesItsMedications() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D1, D1, "MORNING");
        try {
            deleteAs(bearer(f.accountId()), "/api/v1/profiles/" + f.seniorId()).andExpect(status().isOk());

            assertThat(count("SELECT COUNT(*) FROM medications WHERE id = ?", medicationId)).isZero();
            assertThat(count("SELECT COUNT(*) FROM medication_logs WHERE medication_id = ?", medicationId)).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM medication_logs WHERE medication_id = ?", medicationId);
            jdbcTemplate.update("DELETE FROM medications WHERE id = ?", medicationId);
        }
    }

    private ResultActions registerAs(String token, long seniorId, Map<String, Object> item) throws Exception {
        item.put("senior_profile_id", seniorId);
        return postJson(token, "/api/medications", item);
    }

    @Test
    void inactiveAccountCannotChangeMedications() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D1, D1, "MORNING");
        String token = bearer(f.accountId());
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", f.accountId());

        registerAs(token, f.seniorId(), medicationItem("약", D1, D1, "MORNING"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
        putJson(token, "/api/medications/" + medicationId, Map.of("frequency", "바뀜"))
                .andExpect(status().isForbidden());
        deleteAs(token, "/api/medications/" + medicationId).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM medications WHERE id = ?", medicationId)).isEqualTo(1);
    }
}
