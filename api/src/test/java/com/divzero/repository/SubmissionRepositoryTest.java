package com.divzero.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.divzero.entity.ProblemEntity;
import com.divzero.entity.Role;
import com.divzero.entity.SubmissionEntity;
import com.divzero.entity.SubmissionStatus;
import com.divzero.entity.UserEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Postgres, not H2: the leaderboard query is native Postgres SQL (CTEs, FILTER, quoted
 * camelCase identifiers) that H2 does not implement.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "app.jwt.secret=repository-test-secret-0123456789abcdef"
})
class SubmissionRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    private String aliceId;
    private String bobId;
    private String problemOneId;
    private String problemTwoId;

    @BeforeEach
    void seed() {
        aliceId = persistUser("Alice", "alice");
        bobId = persistUser("Bob", "bob");
        problemOneId = persistProblem("Problem One");
        problemTwoId = persistProblem("Problem Two");
    }

    private String persistUser(String name, String username) {
        UserEntity user = UserEntity.builder()
            .name(name)
            .username(username)
            .password("hashed")
            .role(Role.USER)
            .build();
        entityManager.persist(user);
        return user.getId();
    }

    private String persistProblem(String title) {
        ProblemEntity problem = ProblemEntity.builder()
            .title(title)
            .description("description")
            .build();
        entityManager.persist(problem);
        return problem.getId();
    }

    private void persistSubmission(
        String userId,
        String problemId,
        SubmissionStatus status,
        long executionTimeMs
    ) {
        SubmissionEntity submission = SubmissionEntity.builder()
            .userId(userId)
            .problemId(problemId)
            .status(status)
            .verdict(status.displayVerdict())
            .language("cpp")
            .compileTimeMs(10L)
            .executionTimeMs(executionTimeMs)
            .judgeLogs("")
            .build();
        entityManager.persist(submission);
    }

    private LeaderboardRow rowFor(String userId) {
        // Native queries bypass the persistence context, so pending inserts must be flushed first.
        entityManager.flush();
        return submissionRepository.findLeaderboard().stream()
            .filter(row -> row.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no leaderboard row for " + userId));
    }

    @Test
    @DisplayName("three ACCEPTED submissions on one problem count as one problem solved")
    void repeatedAcceptsOnOneProblemCountOnce() {
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 300L);
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 100L);
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 200L);
        persistSubmission(aliceId, problemTwoId, SubmissionStatus.ACCEPTED, 700L);

        LeaderboardRow alice = rowFor(aliceId);

        // The whole point of Task 5: four accepted rows, but only two distinct problems.
        assertThat(alice.getProblemsSolved()).isEqualTo(2L);
        assertThat(alice.getTotalSubmissions()).isEqualTo(4L);
    }

    @Test
    @DisplayName("the time tiebreaker sums the best accepted run per problem, not every attempt")
    void tiebreakerSumsTheBestRunPerProblem() {
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 300L);
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 100L);
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 200L);
        persistSubmission(aliceId, problemTwoId, SubmissionStatus.ACCEPTED, 700L);

        // min(300, 100, 200) + 700 -- repeats neither inflate nor deflate the total.
        assertThat(rowFor(aliceId).getTotalExecutionTimeMs()).isEqualTo(800L);
    }

    @Test
    @DisplayName("non-accepted submissions count as attempts but never as solved problems")
    void rejectedSubmissionsDoNotCountAsSolved() {
        persistSubmission(bobId, problemOneId, SubmissionStatus.WRONG_ANSWER, 50L);
        persistSubmission(bobId, problemOneId, SubmissionStatus.TIME_LIMIT_EXCEEDED, 60L);
        persistSubmission(bobId, problemTwoId, SubmissionStatus.COMPILATION_ERROR, 0L);

        LeaderboardRow bob = rowFor(bobId);

        assertThat(bob.getProblemsSolved()).isZero();
        assertThat(bob.getTotalSubmissions()).isEqualTo(3L);
        assertThat(bob.getTotalExecutionTimeMs()).isZero();
    }

    @Test
    @DisplayName("a user who has never submitted still appears, ranked last")
    void usersWithoutSubmissionsStillAppear() {
        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 100L);
        entityManager.flush();

        List<LeaderboardRow> leaderboard = submissionRepository.findLeaderboard();

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).getUserId()).isEqualTo(aliceId);
        assertThat(leaderboard.get(1).getUserId()).isEqualTo(bobId);
        assertThat(leaderboard.get(1).getProblemsSolved()).isZero();
    }

    @Test
    @DisplayName("existsByUserIdAndProblemIdAndStatus drives the first-solve cache eviction")
    void existsByUserIdAndProblemIdAndStatus() {
        persistSubmission(aliceId, problemOneId, SubmissionStatus.WRONG_ANSWER, 10L);
        entityManager.flush();

        assertThat(submissionRepository
            .existsByUserIdAndProblemIdAndStatus(aliceId, problemOneId, SubmissionStatus.ACCEPTED))
            .isFalse();

        persistSubmission(aliceId, problemOneId, SubmissionStatus.ACCEPTED, 10L);
        entityManager.flush();

        assertThat(submissionRepository
            .existsByUserIdAndProblemIdAndStatus(aliceId, problemOneId, SubmissionStatus.ACCEPTED))
            .isTrue();
    }
}
