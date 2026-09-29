package com.divzero.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.divzero.dto.JudgeResult;
import com.divzero.entity.ProblemEntity;
import com.divzero.entity.Role;
import com.divzero.entity.SubmissionEntity;
import com.divzero.entity.SubmissionStatus;
import com.divzero.entity.UserEntity;
import com.divzero.event.SubmissionCompletedEvent;
import com.divzero.event.SubmissionCreatedEvent;
import com.divzero.event.SubmissionEventPublisher;
import com.divzero.repository.ProblemRepository;
import com.divzero.repository.SubmissionRepository;
import com.divzero.repository.UserRepository;
import com.divzero.service.JudgeService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Exercises the real Kafka round trip: SubmissionCreated is consumed by the judge worker, the
 * resulting SubmissionCompleted is consumed by the completion listener, and the submission row
 * reaches a terminal status.
 *
 * <p>The most valuable assertion here is the last one: replaying a completion event must not
 * overwrite an already-terminal submission. Every API instance consumes every completion event
 * (Task 7), so duplicate delivery is the normal case, not an edge case.
 */
// The default MOCK web environment is required: SecurityConfig.securityFilterChain needs the
// HttpSecurity bean, which only exists in a servlet web context. No HTTP is issued here.
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "app.jwt.secret=pipeline-integration-test-secret-0123456789",
    "spring.cache.type=none"
})
class SubmissionPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Postgres is wired with @ServiceConnection, but Kafka cannot be: Spring Boot 3.3 ships
     * connection-details factories only for the older Confluent-based KafkaContainer, and support
     * for org.testcontainers.kafka.KafkaContainer arrives in Boot 3.4. Registering the bootstrap
     * servers by hand keeps the test on apache/kafka:3.7.0 -- the same broker image the compose
     * stack runs -- instead of swapping in a different broker just to satisfy the annotation.
     */
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL = Duration.ofMillis(250);

    /** Stubbed so the pipeline can be exercised without Docker-in-Docker for the sandbox. */
    @MockBean
    private JudgeService judgeService;

    @Autowired
    private SubmissionEventPublisher eventPublisher;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    private String userId;
    private String problemId;

    @BeforeEach
    void seed() {
        userId = userRepository.save(UserEntity.builder()
            .name("Ada")
            .username("ada-" + System.nanoTime())
            .password("hashed")
            .role(Role.USER)
            .build()).getId();

        problemId = problemRepository.save(ProblemEntity.builder()
            .title("Sum")
            .description("add two numbers")
            .build()).getId();

        given(judgeService.judge(any(SubmissionCreatedEvent.class))).willReturn(acceptedResult());
    }

    private static JudgeResult acceptedResult() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Accepted");
        body.put("status", "All tests passed!");
        return new JudgeResult(
            SubmissionStatus.ACCEPTED,
            SubmissionStatus.ACCEPTED.displayVerdict(),
            body,
            12L,
            34L,
            2048L,
            "cpp",
            "[stubbed judge]"
        );
    }

    private String queuedSubmission() {
        return submissionRepository.save(SubmissionEntity.builder()
            .userId(userId)
            .problemId(problemId)
            .status(SubmissionStatus.QUEUED)
            .verdict(SubmissionStatus.QUEUED.displayVerdict())
            .language("cpp")
            .compileTimeMs(0L)
            .executionTimeMs(0L)
            .judgeLogs("")
            .build()).getId();
    }

    private SubmissionEntity reload(String submissionId) {
        return submissionRepository.findById(submissionId).orElseThrow();
    }

    @Test
    @DisplayName("a published SubmissionCreated event drives the submission to a terminal status")
    void submissionReachesTerminalStatus() {
        String submissionId = queuedSubmission();

        eventPublisher.publishSubmissionCreated(new SubmissionCreatedEvent(
            submissionId,
            userId,
            problemId,
            "int main(){}",
            "cpp",
            Instant.now()
        ));

        await().atMost(TIMEOUT).pollInterval(POLL).untilAsserted(() -> {
            SubmissionEntity stored = reload(submissionId);
            assertThat(stored.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(stored.getStatus().terminal()).isTrue();
            assertThat(stored.getVerdict()).isEqualTo("Accepted");
            assertThat(stored.getCompileTimeMs()).isEqualTo(12L);
            assertThat(stored.getExecutionTimeMs()).isEqualTo(34L);
            assertThat(stored.getMemoryUsageKb()).isEqualTo(2048L);
        });
    }

    @Test
    @DisplayName("replaying a completion event leaves the terminal submission untouched")
    void duplicateCompletionEventIsIgnored() {
        String submissionId = queuedSubmission();

        SubmissionCompletedEvent accepted = SubmissionCompletedEvent.fromJudgeResult(
            submissionId,
            acceptedResult()
        );
        eventPublisher.publishSubmissionCompleted(accepted);

        await().atMost(TIMEOUT).pollInterval(POLL).untilAsserted(() ->
            assertThat(reload(submissionId).getStatus()).isEqualTo(SubmissionStatus.ACCEPTED)
        );

        // Replay the same submission with a *different* outcome. The terminal-status guard must
        // reject it outright: if the guard were missing, this would overwrite the stored verdict,
        // which is exactly what duplicate delivery across N instances would otherwise cause.
        Map<String, Object> wrongBody = new LinkedHashMap<>();
        wrongBody.put("message", "Failed on test t1 (Wrong Answer)");
        eventPublisher.publishSubmissionCompleted(new SubmissionCompletedEvent(
            submissionId,
            SubmissionStatus.WRONG_ANSWER,
            SubmissionStatus.WRONG_ANSWER.displayVerdict(),
            wrongBody,
            999L,
            999L,
            999L,
            "cpp",
            "[duplicate]",
            Instant.now()
        ));

        // Give the listener room to consume and (correctly) do nothing, then assert nothing moved.
        await()
            .during(Duration.ofSeconds(5))
            .atMost(TIMEOUT)
            .pollInterval(POLL)
            .untilAsserted(() -> {
                SubmissionEntity stored = reload(submissionId);
                assertThat(stored.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
                assertThat(stored.getVerdict()).isEqualTo("Accepted");
                assertThat(stored.getCompileTimeMs()).isEqualTo(12L);
                assertThat(stored.getExecutionTimeMs()).isEqualTo(34L);
            });
    }
}
