package com.divzero.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.divzero.entity.ProblemEntity;
import com.divzero.entity.Role;
import com.divzero.entity.TestCaseEntity;
import com.divzero.entity.UserEntity;
import com.divzero.repository.ProblemRepository;
import com.divzero.repository.TestCaseRepository;
import com.divzero.repository.UserRepository;
import com.divzero.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Asserts that authorization is enforced by the filter chain and method security, rather than by
 * ad-hoc header checks inside services.
 *
 * <p>Kafka listeners are disabled: none of these routes publish or consume, and starting them
 * would require a broker this test has no use for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
    "app.jwt.secret=security-rules-test-secret-0123456789abc",
    "spring.kafka.listener.auto-startup=false",
    // KafkaAdmin would otherwise spend its full timeout trying to create the NewTopic beans
    // against a broker this test does not run.
    "spring.kafka.admin.auto-create=false",
    "spring.cache.type=none"
})
class SecurityRulesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private JwtService jwtService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void issueTokens() {
        userToken = "Bearer " + jwtService.generateToken(persist("Uma", Role.USER));
        adminToken = "Bearer " + jwtService.generateToken(persist("Adam", Role.ADMIN));
    }

    private UserEntity persist(String name, Role role) {
        return userRepository.save(UserEntity.builder()
            .name(name)
            .username(name.toLowerCase() + "-" + System.nanoTime())
            .password("hashed")
            .role(role)
            .build());
    }

    private static String problemJson() {
        return "{\"title\":\"T\",\"description\":\"D\"}";
    }

    // ── unauthenticated ──────────────────────────────────────────────────────

    @Test
    @DisplayName("unauthenticated GET /api/view-problems is 401 with a JSON body")
    void unauthenticatedViewProblemsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/view-problems"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("a garbage token is still unauthenticated, not a server error")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/view-problems").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());
    }

    // ── admin-only routes ────────────────────────────────────────────────────

    @Test
    @DisplayName("a USER token on POST /api/create-problem is 403")
    void userTokenCannotCreateProblem() throws Exception {
        mockMvc.perform(post("/api/create-problem")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(problemJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("an ADMIN token on POST /api/create-problem is not 403")
    void adminTokenMayCreateProblem() throws Exception {
        mockMvc.perform(post("/api/create-problem")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(problemJson()))
            .andExpect(result ->
                org.assertj.core.api.Assertions
                    .assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(403));
    }

    @Test
    @DisplayName("a USER token on GET /api/view-users is 403")
    void userTokenCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/view-users").header("Authorization", userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an ADMIN token on GET /api/view-users is not 403")
    void adminTokenMayListUsers() throws Exception {
        mockMvc.perform(get("/api/view-users").header("Authorization", adminToken))
            .andExpect(result ->
                org.assertj.core.api.Assertions
                    .assertThat(result.getResponse().getStatus())
                    .isNotEqualTo(403));
    }

    @Test
    @DisplayName("GET /api/view-users never includes password hashes")
    void viewUsersOmitsPasswords() throws Exception {
        mockMvc.perform(get("/api/view-users").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].role").exists())
            .andExpect(jsonPath("$[*].password").isEmpty());
    }

    // ── authenticated, non-admin routes ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/me returns the caller's role and no password")
    void meReturnsOwnRole() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Uma"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/api/me").header("Authorization", adminToken))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("GET /api/me without a token is 401")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a wrong HTTP method is 405, not a logged 500")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/submit-code").header("Authorization", userToken))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.message").value("Method not allowed"));
    }

    @Test
    @DisplayName("signup rejects a short password and an invalid username with field errors")
    void signupValidatesCredentials() throws Exception {
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bad name!\",\"name\":\"N\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.username").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("submitting to an unknown problem is 404; to a problem without tests is 409")
    void submitRequiresAJudgeableProblem() throws Exception {
        mockMvc.perform(post("/api/submit-code")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"int main(){}\",\"problemId\":\"does-not-exist\"}"))
            .andExpect(status().isNotFound());

        ProblemEntity empty = problemRepository.save(
            ProblemEntity.builder().title("Empty").description("no tests").build()
        );
        mockMvc.perform(post("/api/submit-code")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"int main(){}\",\"problemId\":\"" + empty.getId() + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("This problem has no test cases yet"));
    }

    @Test
    @DisplayName("a USER sees only sample tests; an ADMIN sees hidden ones too")
    void hiddenTestsAreVisibleToAdminsOnly() throws Exception {
        ProblemEntity problem = problemRepository.save(
            ProblemEntity.builder().title("P").description("D").build()
        );
        testCaseRepository.save(TestCaseEntity.builder()
            .problemId(problem.getId()).input("1").correctOutput("1").sample(true).build());
        testCaseRepository.save(TestCaseEntity.builder()
            .problemId(problem.getId()).input("hidden-in").correctOutput("hidden-out").build());

        mockMvc.perform(get("/api/view-tests").param("problemId", problem.getId())
                .header("Authorization", userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sample").value(true));
        mockMvc.perform(get("/api/view-problem").param("problemId", problem.getId())
                .header("Authorization", userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tests.length()").value(1))
            .andExpect(jsonPath("$.tests[0].input").value("1"));

        mockMvc.perform(get("/api/view-tests").param("problemId", problem.getId())
                .header("Authorization", adminToken))
            .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/view-problem").param("problemId", problem.getId())
                .header("Authorization", adminToken))
            .andExpect(jsonPath("$.tests.length()").value(2));
    }

    @Test
    @DisplayName("a USER token may read the problem list")
    void userTokenMayViewProblems() throws Exception {
        mockMvc.perform(get("/api/view-problems").header("Authorization", userToken))
            .andExpect(status().isOk());
    }

    // ── public routes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/health is public")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/leaderboard is public")
    void leaderboardIsPublic() throws Exception {
        mockMvc.perform(get("/api/leaderboard")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/signin is public and rejects bad credentials with 401")
    void signinIsPublicAndRejectsBadCredentials() throws Exception {
        mockMvc.perform(post("/api/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
