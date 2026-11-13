package com.ttareureung.api.domain.profile;

import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.domain.user.repository.AccountRepository;
import com.ttareureung.api.global.security.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=auth-test-secret-key-at-least-32-bytes",
        "jwt.cookie.secure=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class GuardianSeniorApiTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<Long> accountIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @AfterEach
    void cleanUpCreatedAccounts() {
        for (Long id : accountIds) {
            jdbcTemplate.update("""
                    DELETE gs FROM guardian_seniors gs
                    JOIN profiles p ON gs.guardian_profile_id = p.id
                    WHERE p.account_id = ?""", id);
            jdbcTemplate.update("DELETE FROM profiles WHERE account_id = ?", id);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id);
        }
    }

    @Test
    void linksGuardianAndSeniorInSameAccount() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "홍길동", "GUARDIAN", "첫째 아들");
        long seniorId = createProfile(accountId, "김순자", "SENIOR", "어머니");

        link(accountId, guardianId, seniorId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.linkId").isNumber())
                .andExpect(jsonPath("$.data.guardian.profileId").value(guardianId))
                .andExpect(jsonPath("$.data.guardian.name").value("홍길동"))
                .andExpect(jsonPath("$.data.guardian.relationship").value("첫째 아들"))
                .andExpect(jsonPath("$.data.senior.profileId").value(seniorId))
                .andExpect(jsonPath("$.data.senior.relationship").value("어머니"));

        var row = jdbcTemplate.queryForMap(
                "SELECT * FROM guardian_seniors WHERE guardian_profile_id = ? AND senior_profile_id = ?", guardianId, seniorId);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void rejectsWrongRolePairs() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자1", "GUARDIAN", null);
        long otherGuardianId = createProfile(accountId, "보호자2", "GUARDIAN", null);
        long seniorId = createProfile(accountId, "시니어1", "SENIOR", null);
        long otherSeniorId = createProfile(accountId, "시니어2", "SENIOR", null);

        for (long[] pair : new long[][]{{seniorId, guardianId}, {guardianId, otherGuardianId}, {seniorId, otherSeniorId}}) {
            link(accountId, pair[0], pair[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROFILE_003"));
        }
        assertThat(linkCount(accountId)).isZero();
    }

    @Test
    void rejectsDuplicateLink() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자", "GUARDIAN", null);
        long seniorId = createProfile(accountId, "시니어", "SENIOR", null);

        link(accountId, guardianId, seniorId).andExpect(status().isCreated());
        link(accountId, guardianId, seniorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_004"));
        assertThat(linkCount(accountId)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateLinksCreateOnlyOne() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자", "GUARDIAN", null);
        long seniorId = createProfile(accountId, "시니어", "SENIOR", null);
        String token = bearer(accountId);

        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> request = () -> {
            start.await();
            return mockMvc.perform(post("/api/v1/guardian-seniors").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(linkJson(guardianId, seniorId)))
                    .andReturn().getResponse().getStatus();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> a = pool.submit(request);
            Future<Integer> b = pool.submit(request);
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(linkCount(accountId)).isEqualTo(1);
    }

    @Test
    void cannotLinkProfilesOfAnotherAccount() throws Exception {
        long mine = newAccount();
        long other = newAccount();
        long myGuardian = createProfile(mine, "내 보호자", "GUARDIAN", null);
        long mySenior = createProfile(mine, "내 시니어", "SENIOR", null);
        createProfile(other, "남의 보호자", "GUARDIAN", null);
        long otherSenior = createProfile(other, "남의 시니어", "SENIOR", null);

        link(mine, myGuardian, otherSenior)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        // 다른 계정 토큰으로 내 프로필끼리 연결 시도
        link(other, myGuardian, mySenior)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        assertThat(linkCount(mine)).isZero();
    }

    @Test
    void listFiltersByProfileAndStaysInsideAccount() throws Exception {
        long accountId = newAccount();
        long guardian1 = createProfile(accountId, "홍길동", "GUARDIAN", null);
        long guardian2 = createProfile(accountId, "아무개", "GUARDIAN", null);
        long senior1 = createProfile(accountId, "김순자", "SENIOR", null);
        long senior2 = createProfile(accountId, "박종옥", "SENIOR", null);
        link(accountId, guardian1, senior1).andExpect(status().isCreated());
        link(accountId, guardian1, senior2).andExpect(status().isCreated());
        link(accountId, guardian2, senior1).andExpect(status().isCreated());

        long other = newAccount();
        long otherGuardian = createProfile(other, "남의 보호자", "GUARDIAN", null);
        long otherSenior = createProfile(other, "남의 시니어", "SENIOR", null);
        link(other, otherGuardian, otherSenior).andExpect(status().isCreated());

        list(accountId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        // 보호자 홈 상단 시니어 탭
        list(accountId, guardian1)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].senior.name").value("김순자"))
                .andExpect(jsonPath("$.data[1].senior.name").value("박종옥"));
        list(accountId, senior1)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].guardian.name").value("홍길동"))
                .andExpect(jsonPath("$.data[1].guardian.name").value("아무개"));
        list(accountId, otherGuardian)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
    }

    @Test
    void emptyListWhenGuardianHasNoSenior() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자", "GUARDIAN", null);

        list(accountId, guardianId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void unlinkRemovesOnlyTheLink() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자", "GUARDIAN", null);
        long seniorId = createProfile(accountId, "시니어", "SENIOR", null);
        long linkId = linkId(link(accountId, guardianId, seniorId));

        unlink(bearer(accountId), linkId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(linkCount(accountId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE account_id = ?", Long.class, accountId))
                .isEqualTo(2);
        // 해제 후 다시 연결 가능
        link(accountId, guardianId, seniorId).andExpect(status().isCreated());
    }

    @Test
    void cannotUnlinkOtherAccountsOrMissingLink() throws Exception {
        long owner = newAccount();
        long guardianId = createProfile(owner, "보호자", "GUARDIAN", null);
        long seniorId = createProfile(owner, "시니어", "SENIOR", null);
        long linkId = linkId(link(owner, guardianId, seniorId));

        unlink(bearer(newAccount()), linkId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_005"));
        unlink(bearer(owner), Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_005"));
        mockMvc.perform(delete("/api/v1/guardian-seniors/abc").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/guardian-seniors/{id}", linkId))
                .andExpect(status().isUnauthorized());
        assertThat(linkCount(owner)).isEqualTo(1);
    }

    @Test
    void inactiveAccountCannotUnlink() throws Exception {
        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자", "GUARDIAN", null);
        long seniorId = createProfile(accountId, "시니어", "SENIOR", null);
        long linkId = linkId(link(accountId, guardianId, seniorId));
        String token = bearer(accountId);
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", accountId);

        unlink(token, linkId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
        assertThat(linkCount(accountId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "invalid link request {index}")
    @ValueSource(strings = {"{}", "{\"guardianProfileId\":1}", "{\"seniorProfileId\":1}", "{\"guardianProfileId\":\"abc\",\"seniorProfileId\":1}"})
    void rejectsInvalidLinkRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/guardian-seniors").header(HttpHeaders.AUTHORIZATION, bearer(newAccount()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void requiresTokenAndActiveAccount() throws Exception {
        mockMvc.perform(get("/api/v1/guardian-seniors")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/guardian-seniors").contentType(MediaType.APPLICATION_JSON).content(linkJson(1, 2)))
                .andExpect(status().isUnauthorized());

        long accountId = newAccount();
        long guardianId = createProfile(accountId, "보호자", "GUARDIAN", null);
        long seniorId = createProfile(accountId, "시니어", "SENIOR", null);
        String token = bearer(accountId);
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", accountId);

        mockMvc.perform(post("/api/v1/guardian-seniors").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(linkJson(guardianId, seniorId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void rejectsNonNumericProfileIdQuery() throws Exception {
        mockMvc.perform(get("/api/v1/guardian-seniors").param("profileId", "abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccount())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    private long newAccount() {
        Account account = accountRepository.save(new Account("link-" + UUID.randomUUID() + "@example.com", "encoded"));
        accountIds.add(account.getId());
        return account.getId();
    }

    private String bearer(long accountId) {
        return "Bearer " + jwtTokenService.issue(accountId).accessToken();
    }

    private long createProfile(long accountId, String name, String role, String relationship) throws Exception {
        var request = new java.util.LinkedHashMap<String, Object>(Map.of("name", name, "role", role));
        if (relationship != null) {
            request.put("relationship", relationship);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("profileId").asLong();
    }

    private ResultActions link(long accountId, long guardianId, long seniorId) throws Exception {
        return mockMvc.perform(post("/api/v1/guardian-seniors").header(HttpHeaders.AUTHORIZATION, bearer(accountId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(linkJson(guardianId, seniorId)));
    }

    private ResultActions list(long accountId, Long profileId) throws Exception {
        var request = get("/api/v1/guardian-seniors").header(HttpHeaders.AUTHORIZATION, bearer(accountId));
        if (profileId != null) {
            request.param("profileId", profileId.toString());
        }
        return mockMvc.perform(request);
    }

    private long linkId(ResultActions linked) throws Exception {
        MvcResult result = linked.andExpect(status().isCreated()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("linkId").asLong();
    }

    private ResultActions unlink(String token, long linkId) throws Exception {
        return mockMvc.perform(delete("/api/v1/guardian-seniors/{id}", linkId).header(HttpHeaders.AUTHORIZATION, token));
    }

    private String linkJson(long guardianId, long seniorId) {
        return jsonMapper.writeValueAsString(Map.of("guardianProfileId", guardianId, "seniorProfileId", seniorId));
    }

    private long linkCount(long accountId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM guardian_seniors gs
                JOIN profiles p ON gs.guardian_profile_id = p.id
                WHERE p.account_id = ?""", Long.class, accountId);
    }
}
