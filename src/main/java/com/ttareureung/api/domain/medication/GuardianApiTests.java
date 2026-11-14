package com.ttareureung.api.domain.guardian;

import com.ttareureung.api.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuardianApiTests extends ApiTestSupport {

    private static final String D = "2026-11-04";

    @Test
    void changesPrimaryGuardianAndKeepsExactlyOne() throws Exception {
        Family f = newFamily();
        long guardian2 = createProfile(f.accountId(), "아무개", "GUARDIAN", null);
        linkProfiles(f.accountId(), guardian2, f.seniorId());
        String token = bearer(f.accountId());

        changePrimary(token, f.seniorId(), guardian2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(guardian2);

        changePrimary(token, f.seniorId(), f.guardianId()).andExpect(status().isOk());
        assertThat(primaryGuardianIds(f.seniorId())).containsExactly(f.guardianId());
    }

    @Test
    void primaryRejectsUnlinkedGuardianOtherAccountAndBadRequests() throws Exception {
        Family f = newFamily();
        long unlinked = createProfile(f.accountId(), "미연결", "GUARDIAN", null);
        String token = bearer(f.accountId());

        changePrimary(token, f.seniorId(), unlinked)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUARDIAN_001"));
        changePrimary(bearer(newAccount()), f.seniorId(), f.guardianId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        patchJson(token, "/api/guardians/primary", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        mockMvc.perform(patch("/api/guardians/primary").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invitesNormalizedNumbersOnlyOnce() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());

        invite(token, f.seniorId(), "010-1234-5678", "01012345678", "010-2222-3333")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invited_count").value(2));

        assertThat(count("SELECT COUNT(*) FROM guardian_invitations WHERE senior_profile_id = ? AND status = 'PENDING'",
                f.seniorId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone_number FROM guardian_invitations WHERE senior_profile_id = ? ORDER BY id LIMIT 1",
                String.class, f.seniorId())).isEqualTo("01012345678");
        assertThat(invitationToken(f.seniorId())).hasSize(32);

        invite(token, f.seniorId(), "010-1234-5678")
                .andExpect(jsonPath("$.data.invited_count").value(0));
        assertThat(count("SELECT COUNT(*) FROM guardian_invitations WHERE senior_profile_id = ?", f.seniorId()))
                .isEqualTo(2);
    }

    @Test
    void skipsNumbersOfAlreadyLinkedGuardians() throws Exception {
        Family f = newFamily();
        long guardian2 = createProfile(f.accountId(), "아무개", "GUARDIAN", "010-9999-0000");
        linkProfiles(f.accountId(), guardian2, f.seniorId());

        invite(bearer(f.accountId()), f.seniorId(), "01099990000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invited_count").value(0));
    }

    @ParameterizedTest(name = "invalid invitation {index}")
    @ValueSource(strings = {
            "{\"phone_numbers\":[\"010-1234-5678\"]}",
            "{\"senior_profile_id\":1,\"phone_numbers\":[]}",
            "{\"senior_profile_id\":1,\"phone_numbers\":[\"abc\"]}",
            "{\"senior_profile_id\":1,\"phone_numbers\":[\"01011110001\",\"01011110002\",\"01011110003\","
                    + "\"01011110004\",\"01011110005\",\"01011110006\",\"01011110007\",\"01011110008\","
                    + "\"01011110009\",\"01011110010\",\"01011110011\"]}"
    })
    void rejectsInvalidInvitationRequest(String body) throws Exception {
        postJson(bearer(newAccount()), "/api/guardians/invitation", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void invitationRequiresAccessibleSenior() throws Exception {
        Family f = newFamily();

        invite(bearer(newAccount()), f.seniorId(), "010-1234-5678")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        invite(bearer(f.accountId()), f.guardianId(), "010-1234-5678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDICATION_003"));
        mockMvc.perform(post("/api/guardians/invitation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senior_profile_id\":1,\"phone_numbers\":[\"010-1234-5678\"]}"))
                .andExpect(status().isUnauthorized());
        assertThat(count("SELECT COUNT(*) FROM guardian_invitations WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }

    @Test
    void acceptLinksGuardianOfAnotherAccountAndGrantsAccess() throws Exception {
        Family f = newFamily();
        long medicationId = registerMedication(f.accountId(), f.seniorId(), "혈압약", D, D, "MORNING");
        invite(bearer(f.accountId()), f.seniorId(), "010-1234-5678").andExpect(status().isOk());
        String inviteToken = invitationToken(f.seniorId());

        long accountB = newAccount();
        long guardianB = createProfile(accountB, "초대받은 보호자", "GUARDIAN", "010-1234-5678");
        String tokenB = bearer(accountB);

        getAs(tokenB, scheduleUrl(f.seniorId(), D))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));

        accept(tokenB, inviteToken, guardianB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.senior_profile_id").value(f.seniorId()));

        var link = jdbcTemplate.queryForMap(
                "SELECT * FROM guardian_seniors WHERE guardian_profile_id = ? AND senior_profile_id = ?",
                guardianB, f.seniorId());
        assertThat(link.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM guardian_invitations WHERE senior_profile_id = ?", String.class, f.seniorId()))
                .isEqualTo("ACCEPTED");

        getAs(tokenB, scheduleUrl(f.seniorId(), D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.time_slots.length()").value(1));
        patchJson(tokenB, "/api/medications/logs/" + logId(medicationId, "MORNING", D), Map.of("status", "TAKEN"))
                .andExpect(status().isOk());

        getAs(bearer(newAccount()), scheduleUrl(f.seniorId(), D)).andExpect(status().isNotFound());
    }

    @Test
    void acceptRejectsWrongProfilesReusedExpiredAndUnknownTokens() throws Exception {
        Family f = newFamily();
        invite(bearer(f.accountId()), f.seniorId(), "010-1234-5678", "010-5555-6666").andExpect(status().isOk());
        List<String> tokens = jdbcTemplate.queryForList(
                "SELECT token FROM guardian_invitations WHERE senior_profile_id = ? ORDER BY id",
                String.class, f.seniorId());
        String first = tokens.get(0);
        String second = tokens.get(1);

        long accountB = newAccount();
        long guardianB = createProfile(accountB, "보호자B", "GUARDIAN", null);
        long seniorB = createSenior(accountB, "시니어B", guardianB);
        String tokenB = bearer(accountB);

        accept(tokenB, first, seniorB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUARDIAN_004"));
        accept(tokenB, first, f.guardianId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        accept(tokenB, "unknown-token", guardianB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUARDIAN_002"));
        postJson(tokenB, "/api/guardians/invitation/accept", Map.of("token", " ", "guardian_profile_id", guardianB))
                .andExpect(status().isBadRequest());

        accept(tokenB, first, guardianB).andExpect(status().isOk());
        accept(tokenB, first, guardianB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUARDIAN_002"));

        jdbcTemplate.update("UPDATE guardian_invitations SET expires_at = ? WHERE token = ?",
                LocalDateTime.now().minusDays(1), second);
        accept(tokenB, second, guardianB)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GUARDIAN_003"));

        assertThat(count("SELECT COUNT(*) FROM guardian_seniors WHERE guardian_profile_id = ? AND senior_profile_id = ?",
                guardianB, f.seniorId())).isEqualTo(1);
    }

    private ResultActions changePrimary(String token, long seniorId, long guardianId) throws Exception {
        return patchJson(token, "/api/guardians/primary",
                Map.of("senior_profile_id", seniorId, "guardian_id", guardianId));
    }

    private ResultActions invite(String token, long seniorId, String... phones) throws Exception {
        return postJson(token, "/api/guardians/invitation",
                Map.of("senior_profile_id", seniorId, "phone_numbers", List.of(phones)));
    }

    private ResultActions accept(String token, String inviteToken, long guardianProfileId) throws Exception {
        return postJson(token, "/api/guardians/invitation/accept",
                Map.of("token", inviteToken, "guardian_profile_id", guardianProfileId));
    }

    private List<Long> primaryGuardianIds(long seniorId) {
        return jdbcTemplate.queryForList(
                "SELECT guardian_profile_id FROM guardian_seniors WHERE senior_profile_id = ? AND is_primary = TRUE",
                Long.class, seniorId);
    }

    private String invitationToken(long seniorId) {
        return jdbcTemplate.queryForObject(
                "SELECT token FROM guardian_invitations WHERE senior_profile_id = ? ORDER BY id LIMIT 1",
                String.class, seniorId);
    }

    @Test
    void inactiveAccountCannotInviteOrChangePrimary() throws Exception {
        Family f = newFamily();
        String token = bearer(f.accountId());
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", f.accountId());

        invite(token, f.seniorId(), "010-1234-5678")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
        changePrimary(token, f.seniorId(), f.guardianId()).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM guardian_invitations WHERE senior_profile_id = ?", f.seniorId())).isZero();
    }
}
