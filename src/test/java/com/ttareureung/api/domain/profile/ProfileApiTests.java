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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=auth-test-secret-key-at-least-32-bytes",
        "jwt.cookie.secure=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ProfileApiTests {

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
    void createsSeniorProfileWithAllFields() throws Exception {
        long accountId = newAccount();
        create(accountId, profile("보호자", "GUARDIAN")).andExpect(status().isCreated());
        var request = profile("  김순자  ", "SENIOR");
        request.put("birthDate", "1950-03-15");
        request.put("gender", "FEMALE");
        request.put("phoneNumber", "010-1234-5678");
        request.put("relationship", " 어머니 ");

        MvcResult result = create(accountId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("김순자"))
                .andExpect(jsonPath("$.data.birthDate").value("1950-03-15"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.relationship").value("어머니"))
                .andExpect(jsonPath("$.data.role").value("SENIOR"))
                .andReturn();

        long profileId = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("profileId").asLong();
        var row = jdbcTemplate.queryForMap("SELECT * FROM profiles WHERE id = ?", profileId);
        assertThat(((Number) row.get("account_id")).longValue()).isEqualTo(accountId);
        assertThat(row.get("role")).isEqualTo("SENIOR");
        assertThat(row.get("relationship")).isEqualTo("어머니");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void createsGuardianProfileWithOnlyRequiredFields() throws Exception {
        long accountId = newAccount();
        var request = profile("이희창", "GUARDIAN");
        request.put("phoneNumber", " ");
        request.put("relationship", "");

        create(accountId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("GUARDIAN"))
                .andExpect(jsonPath("$.data.birthDate").isEmpty())
                .andExpect(jsonPath("$.data.gender").isEmpty())
                .andExpect(jsonPath("$.data.phoneNumber").isEmpty())
                .andExpect(jsonPath("$.data.relationship").isEmpty());
    }

    @Test
    void listReturnsOnlyMyProfilesInCreatedOrder() throws Exception {
        long mine = newAccount();
        long other = newAccount();
        create(mine, profile("보호자", "GUARDIAN")).andExpect(status().isCreated());
        create(other, profile("다른 계정", "GUARDIAN")).andExpect(status().isCreated());
        create(mine, profile("시니어", "SENIOR")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("보호자"))
                .andExpect(jsonPath("$.data[1].name").value("시니어"));
    }

    @Test
    void listIsEmptyRightAfterSignup() throws Exception {
        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(newAccount())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void seniorRequiresGuardianProfileFirst() throws Exception {
        long accountId = newAccount();

        create(accountId, profile("시니어", "SENIOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE account_id = ?", Long.class, accountId))
                .isZero();

        create(accountId, profile("보호자", "GUARDIAN")).andExpect(status().isCreated());
        create(accountId, profile("시니어", "SENIOR")).andExpect(status().isCreated());
    }

    @Test
    void guardianInAnotherAccountDoesNotCount() throws Exception {
        long other = newAccount();
        create(other, profile("다른 계정 보호자", "GUARDIAN")).andExpect(status().isCreated());

        create(newAccount(), profile("시니어", "SENIOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
    }

    @Test
    void findsOwnProfileDetail() throws Exception {
        long accountId = newAccount();
        var request = profile("보호자", "GUARDIAN");
        request.put("relationship", "첫째 아들");
        long profileId = createAndGetId(accountId, request);

        mockMvc.perform(get("/api/v1/profiles/{id}", profileId).header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value(profileId))
                .andExpect(jsonPath("$.data.name").value("보호자"))
                .andExpect(jsonPath("$.data.relationship").value("첫째 아들"))
                .andExpect(jsonPath("$.data.role").value("GUARDIAN"));
    }

    @Test
    void otherAccountsProfileLooksLikeNotFound() throws Exception {
        long owner = newAccount();
        long profileId = createAndGetId(owner, profile("보호자", "GUARDIAN"));
        String stranger = bearer(newAccount());

        mockMvc.perform(get("/api/v1/profiles/{id}", profileId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        update(stranger, profileId, "{\"name\":\"바꿈\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM profiles WHERE id = ?", String.class, profileId))
                .isEqualTo("보호자");

        mockMvc.perform(get("/api/v1/profiles/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
    }

    @Test
    void rejectsNonNumericProfileIdAndMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/abc").header(HttpHeaders.AUTHORIZATION, bearer(newAccount())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        mockMvc.perform(get("/api/v1/profiles/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/profiles/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateChangesOnlySentFields() throws Exception {
        long accountId = newAccount();
        var request = profile("보호자", "GUARDIAN");
        request.put("birthDate", "1970-01-01");
        request.put("gender", "MALE");
        request.put("phoneNumber", "010-1111-2222");
        long profileId = createAndGetId(accountId, request);

        update(bearer(accountId), profileId, "{\"name\":\" 홍길동 \",\"relationship\":\"첫째 아들\",\"role\":\"SENIOR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.relationship").value("첫째 아들"))
                .andExpect(jsonPath("$.data.birthDate").value("1970-01-01"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1111-2222"))
                .andExpect(jsonPath("$.data.role").value("GUARDIAN"));

        var row = jdbcTemplate.queryForMap("SELECT * FROM profiles WHERE id = ?", profileId);
        assertThat(row.get("name")).isEqualTo("홍길동");
        assertThat(row.get("relationship")).isEqualTo("첫째 아들");
        assertThat(row.get("role")).isEqualTo("GUARDIAN");
    }

    @Test
    void updateClearsPhoneAndRelationshipWithEmptyString() throws Exception {
        long accountId = newAccount();
        var request = profile("보호자", "GUARDIAN");
        request.put("phoneNumber", "010-1111-2222");
        request.put("relationship", "첫째 아들");
        long profileId = createAndGetId(accountId, request);

        update(bearer(accountId), profileId, "{\"phoneNumber\":\"\",\"relationship\":\" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").isEmpty())
                .andExpect(jsonPath("$.data.relationship").isEmpty())
                .andExpect(jsonPath("$.data.name").value("보호자"));

        update(bearer(accountId), profileId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("보호자"));
    }

    @ParameterizedTest(name = "invalid update case {index}")
    @ValueSource(strings = {
            "{\"name\":\"   \"}",
            "{\"phoneNumber\":\"abc-1234\"}",
            "{\"gender\":\"OTHER\"}",
            "{\"birthDate\":\"1950/03/15\"}",
            "{\"relationship\":\"가나다라마바사아자차카타파하가나다라마바사\"}"
    })
    void rejectsInvalidUpdate(String body) throws Exception {
        long accountId = newAccount();
        long profileId = createAndGetId(accountId, profile("보호자", "GUARDIAN"));

        update(bearer(accountId), profileId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void rejectsTooLongNameAndFutureBirthDateOnUpdate() throws Exception {
        long accountId = newAccount();
        long profileId = createAndGetId(accountId, profile("보호자", "GUARDIAN"));

        update(bearer(accountId), profileId, "{\"name\":\"" + "가".repeat(51) + "\"}")
                .andExpect(status().isBadRequest());
        update(bearer(accountId), profileId, "{\"birthDate\":\"" + LocalDate.now().plusDays(1) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveAccountCannotUpdateProfile() throws Exception {
        long accountId = newAccount();
        String token = bearer(accountId);
        long profileId = createAndGetId(accountId, profile("보호자", "GUARDIAN"));
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", accountId);

        update(token, profileId, "{\"name\":\"바꿈\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void createsSeniorLinkedToGuardianInOneRequest() throws Exception {
        long accountId = newAccount();
        long guardianId = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        var request = profile("김순자", "SENIOR");
        request.put("guardianProfileId", guardianId);

        MvcResult result = create(accountId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.linkedProfiles.length()").value(1))
                .andExpect(jsonPath("$.data.linkedProfiles[0].profileId").value(guardianId))
                .andExpect(jsonPath("$.data.linkedProfiles[0].name").value("홍길동"))
                .andReturn();
        long seniorId = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("profileId").asLong();

        var link = jdbcTemplate.queryForMap("SELECT * FROM guardian_seniors WHERE senior_profile_id = ?", seniorId);
        assertThat(((Number) link.get("guardian_profile_id")).longValue()).isEqualTo(guardianId);
        assertThat(link.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void invalidGuardianProfileIdRollsBackSeniorCreation() throws Exception {
        long accountId = newAccount();
        long guardianId = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        long otherGuardianId = createAndGetId(newAccount(), profile("남의 보호자", "GUARDIAN"));

        var seniorAsGuardian = profile("시니어1", "SENIOR");
        seniorAsGuardian.put("guardianProfileId", otherGuardianId);
        create(accountId, seniorAsGuardian)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));

        var guardianWithLink = profile("보호자2", "GUARDIAN");
        guardianWithLink.put("guardianProfileId", guardianId);
        create(accountId, guardianWithLink)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_003"));

        long seniorId = createAndGetId(accountId, profile("시니어2", "SENIOR"));
        var linkToSenior = profile("시니어3", "SENIOR");
        linkToSenior.put("guardianProfileId", seniorId);
        create(accountId, linkToSenior)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_003"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE account_id = ?", Long.class, accountId))
                .isEqualTo(2);
    }

    @Test
    void listAndDetailIncludeLinkedProfiles() throws Exception {
        long accountId = newAccount();
        long guardianId = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        long subGuardianId = createAndGetId(accountId, profile("아무개", "GUARDIAN"));
        var senior = profile("김순자", "SENIOR");
        senior.put("guardianProfileId", guardianId);
        long seniorId = createAndGetId(accountId, senior);

        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].linkedProfiles[0].name").value("김순자"))
                .andExpect(jsonPath("$.data[1].linkedProfiles.length()").value(0))
                .andExpect(jsonPath("$.data[2].linkedProfiles[0].name").value("홍길동"));

        mockMvc.perform(get("/api/v1/profiles/{id}", seniorId).header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(jsonPath("$.data.linkedProfiles[0].profileId").value(guardianId));
        mockMvc.perform(get("/api/v1/profiles/{id}", subGuardianId).header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(jsonPath("$.data.linkedProfiles.length()").value(0));
    }

    @Test
    void deletingSeniorAlsoRemovesItsLinks() throws Exception {
        long accountId = newAccount();
        long guardianId = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        long seniorId = createLinkedSenior(accountId, "김순자", guardianId);

        deleteProfile(bearer(accountId), seniorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE id = ?", Long.class, seniorId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM guardian_seniors WHERE senior_profile_id = ?", Long.class, seniorId)).isZero();
        mockMvc.perform(get("/api/v1/profiles/{id}", guardianId).header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(jsonPath("$.data.linkedProfiles.length()").value(0));
    }

    @Test
    void deletingOneOfSeveralGuardiansKeepsOtherLinks() throws Exception {
        long accountId = newAccount();
        long guardian1 = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        long guardian2 = createAndGetId(accountId, profile("아무개", "GUARDIAN"));
        long seniorId = createLinkedSenior(accountId, "김순자", guardian1);
        mockMvc.perform(post("/api/v1/guardian-seniors").header(HttpHeaders.AUTHORIZATION, bearer(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("guardianProfileId", guardian2, "seniorProfileId", seniorId))))
                .andExpect(status().isCreated());

        deleteProfile(bearer(accountId), guardian1).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/profiles/{id}", seniorId).header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(jsonPath("$.data.linkedProfiles.length()").value(1))
                .andExpect(jsonPath("$.data.linkedProfiles[0].profileId").value(guardian2));
    }

    @Test
    void lastGuardianCannotBeDeletedWhileSeniorExists() throws Exception {
        long accountId = newAccount();
        long guardianId = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        long seniorId = createLinkedSenior(accountId, "김순자", guardianId);

        deleteProfile(bearer(accountId), guardianId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_006"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE id = ?", Long.class, guardianId)).isEqualTo(1);

        // 시니어를 먼저 지우면 마지막 보호자도 삭제 가능
        deleteProfile(bearer(accountId), seniorId).andExpect(status().isOk());
        deleteProfile(bearer(accountId), guardianId).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(accountId)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void concurrentGuardianDeletesLeaveOneGuardian() throws Exception {
        long accountId = newAccount();
        long guardian1 = createAndGetId(accountId, profile("홍길동", "GUARDIAN"));
        long guardian2 = createAndGetId(accountId, profile("아무개", "GUARDIAN"));
        createLinkedSenior(accountId, "김순자", guardian1);
        String token = bearer(accountId);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> first = () -> {
                start.await();
                return deleteProfile(token, guardian1).andReturn().getResponse().getStatus();
            };
            Callable<Integer> second = () -> {
                start.await();
                return deleteProfile(token, guardian2).andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(first);
            Future<Integer> b = pool.submit(second);
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM profiles WHERE account_id = ? AND role = 'GUARDIAN'", Long.class, accountId)).isEqualTo(1);
    }

    @Test
    void cannotDeleteOtherAccountsOrMissingProfile() throws Exception {
        long owner = newAccount();
        long profileId = createAndGetId(owner, profile("보호자", "GUARDIAN"));

        deleteProfile(bearer(newAccount()), profileId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_002"));
        deleteProfile(bearer(owner), Long.MAX_VALUE)
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/profiles/abc").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/profiles/{id}", profileId))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles WHERE id = ?", Long.class, profileId)).isEqualTo(1);
    }

    @Test
    void inactiveAccountCannotDeleteProfile() throws Exception {
        long accountId = newAccount();
        String token = bearer(accountId);
        long profileId = createAndGetId(accountId, profile("보호자", "GUARDIAN"));
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", accountId);

        deleteProfile(token, profileId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
        mockMvc.perform(post("/api/v1/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(profile("이름", "SENIOR"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveAccountCannotCreateProfile() throws Exception {
        long accountId = newAccount();
        String token = bearer(accountId);
        jdbcTemplate.update("UPDATE accounts SET status = 'INACTIVE' WHERE id = ?", accountId);

        mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(profile("이름", "SENIOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @ParameterizedTest(name = "invalid request case {index}")
    @ValueSource(strings = {
            "{\"role\":\"SENIOR\"}",
            "{\"name\":\"   \",\"role\":\"SENIOR\"}",
            "{\"name\":\"이름\"}",
            "{\"name\":\"이름\",\"role\":\"ADMIN\"}",
            "{\"name\":\"이름\",\"role\":\"SENIOR\",\"gender\":\"OTHER\"}",
            "{\"name\":\"이름\",\"role\":\"SENIOR\",\"phoneNumber\":\"abc-1234\"}",
            "{\"name\":\"이름\",\"role\":\"SENIOR\",\"birthDate\":\"1950/03/15\"}",
            "{\"name\":\"이름\",\"role\":\"GUARDIAN\",\"relationship\":\"가나다라마바사아자차카타파하가나다라마바사\"}"
    })
    void rejectsInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(newAccount()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void rejectsTooLongNameAndFutureBirthDate() throws Exception {
        long accountId = newAccount();
        create(accountId, profile("가".repeat(51), "SENIOR")).andExpect(status().isBadRequest());

        var future = profile("이름", "SENIOR");
        future.put("birthDate", LocalDate.now().plusDays(1).toString());
        create(accountId, future).andExpect(status().isBadRequest());
    }

    @Test
    void swaggerSendsBearerTokenOnlyToProtectedApis() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var docs = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(docs.path("security").get(0).has("bearerAuth")).isTrue();

        // 프로필 API는 전역 Bearer 설정 사용, 별도 토큰 파라미터 없음
        var profileApi = docs.path("paths").path("/api/v1/profiles");
        assertThat(profileApi.path("get").has("security")).isFalse();
        assertThat(profileApi.path("get").path("parameters").isEmpty()).isTrue();
        assertThat(profileApi.path("post").has("security")).isFalse();

        // 인증·Health API는 빈 security로 토큰 전송 제외
        for (String path : List.of("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")) {
            assertThat(docs.path("paths").path(path).path("post").path("security").isEmpty()).as(path).isTrue();
            assertThat(docs.path("paths").path(path).path("post").has("security")).as(path).isTrue();
        }
        assertThat(docs.path("paths").path("/api/v1/health").path("get").has("security")).isTrue();
        assertThat(docs.path("paths").path("/api/v1/health").path("get").path("security").isEmpty()).isTrue();
    }

    private long newAccount() {
        Account account = accountRepository.save(new Account("profile-" + UUID.randomUUID() + "@example.com", "encoded"));
        accountIds.add(account.getId());
        return account.getId();
    }

    private String bearer(long accountId) {
        return "Bearer " + jwtTokenService.issue(accountId).accessToken();
    }

    private Map<String, Object> profile(String name, String role) {
        var request = new LinkedHashMap<String, Object>();
        request.put("name", name);
        request.put("role", role);
        return request;
    }

    private long createAndGetId(long accountId, Map<String, Object> request) throws Exception {
        MvcResult result = create(accountId, request).andExpect(status().isCreated()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("profileId").asLong();
    }

    private ResultActions update(String token, long profileId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/profiles/{id}", profileId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createLinkedSenior(long accountId, String name, long guardianId) throws Exception {
        var request = profile(name, "SENIOR");
        request.put("guardianProfileId", guardianId);
        return createAndGetId(accountId, request);
    }

    private ResultActions deleteProfile(String token, long profileId) throws Exception {
        return mockMvc.perform(delete("/api/v1/profiles/{id}", profileId).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions create(long accountId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(accountId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }
}
