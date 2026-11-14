package com.ttareureung.api.support;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ttareureung.api.domain.account.entity.Account;
import com.ttareureung.api.domain.account.repository.AccountRepository;
import com.ttareureung.api.global.security.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=auth-test-secret-key-at-least-32-bytes",
        "jwt.cookie.secure=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public abstract class ApiTestSupport {

    protected final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<Long> accountIds = new ArrayList<>();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    /** 테스트가 만든 계정과 그 하위 데이터를 FK 순서대로 정리 */
    @AfterEach
    protected void cleanUpCreatedData() {
        for (Long id : accountIds) {
            jdbcTemplate.update("""
                    DELETE ml FROM medication_logs ml
                    JOIN medications m ON ml.medication_id = m.id
                    JOIN profiles p ON m.senior_profile_id = p.id
                    WHERE p.account_id = ?""", id);
            jdbcTemplate.update("""
                    DELETE m FROM medications m
                    JOIN profiles p ON m.senior_profile_id = p.id
                    WHERE p.account_id = ?""", id);
            jdbcTemplate.update("""
                    DELETE gi FROM guardian_invitations gi
                    JOIN profiles p ON gi.senior_profile_id = p.id
                    WHERE p.account_id = ?""", id);
            jdbcTemplate.update("""
                    DELETE gs FROM guardian_seniors gs
                    JOIN profiles p ON gs.guardian_profile_id = p.id OR gs.senior_profile_id = p.id
                    WHERE p.account_id = ?""", id);
            jdbcTemplate.update("DELETE FROM profiles WHERE account_id = ?", id);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id);
        }
    }

    protected record Family(long accountId, long guardianId, long seniorId) {}

    protected long newAccount() {
        Account account = accountRepository.save(new Account("med-" + UUID.randomUUID() + "@example.com", "encoded"));
        accountIds.add(account.getId());
        return account.getId();
    }

    protected String bearer(long accountId) {
        return "Bearer " + jwtTokenService.issue(accountId).accessToken();
    }

    /** 계정 1개 + 보호자 프로필 + 연결된 시니어 프로필 */
    protected Family newFamily() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "홍길동", "GUARDIAN", null);
        long seniorId = createSenior(accountId, "김순자", guardianId);
        return new Family(accountId, guardianId, seniorId);
    }

    protected long createProfile(long accountId, String name, String role, String phone) throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("name", name);
        request.put("role", role);
        if (phone != null) {
            request.put("phoneNumber", phone);
        }
        return idOf(postJson(bearer(accountId), "/api/v1/profiles", request)
                .andExpect(status().isCreated()).andReturn(), "profileId");
    }

    protected long createSenior(long accountId, String name, long guardianProfileId) throws Exception {
        var request = new LinkedHashMap<String, Object>(Map.of(
                "name", name, "role", "SENIOR", "guardianProfileId", guardianProfileId));
        return idOf(postJson(bearer(accountId), "/api/v1/profiles", request)
                .andExpect(status().isCreated()).andReturn(), "profileId");
    }

    protected void linkProfiles(long accountId, long guardianId, long seniorId) throws Exception {
        postJson(bearer(accountId), "/api/v1/guardian-seniors",
                Map.of("guardianProfileId", guardianId, "seniorProfileId", seniorId))
                .andExpect(status().isCreated());
    }

    // 약
    protected Map<String, Object> medicationItem(String drugName, String start, String end, String... slots) {
        var item = new LinkedHashMap<String, Object>();
        item.put("drug_name", drugName);
        item.put("effect", "통증 완화");
        item.put("start_date", start);
        item.put("end_date", end);
        item.put("cycle_type", "DAILY");
        item.put("frequency", "하루 2회, 식후 30분");
        item.put("notify_on_miss", true);
        item.put("meal_time_slots", List.of(slots));
        return item;
    }

    protected long registerMedication(long accountId, long seniorId, String drugName,
                                      String start, String end, String... slots) throws Exception {
        var body = medicationItem(drugName, start, end, slots);
        body.put("senior_profile_id", seniorId);
        return idOf(postJson(bearer(accountId), "/api/medications", body)
                .andExpect(status().isCreated()).andReturn(), "medication_id");
    }

    protected long logId(long medicationId, String slot, String date) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM medication_logs WHERE medication_id = ? AND meal_time_slot = ? AND target_date = ?",
                Long.class, medicationId, slot, date);
    }

    protected String scheduleUrl(long seniorId, String date) {
        return "/api/medications/schedule?senior_id=" + seniorId + "&date=" + date;
    }

    // HTTP / DB
    private String toJson(Object body) {
        return body instanceof String s ? s : jsonMapper.writeValueAsString(body);
    }

    protected ResultActions postJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions putJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions patchJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions getAs(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions deleteAs(String token, String url) throws Exception {
        return mockMvc.perform(delete(url).header(HttpHeaders.AUTHORIZATION, token));
    }

    protected long idOf(MvcResult result, String field) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path(field).asLong();
    }

    protected long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
