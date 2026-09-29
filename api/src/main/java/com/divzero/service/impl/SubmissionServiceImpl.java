package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.JudgeResult;
import com.divzero.dto.RateLimitDecision;
import com.divzero.dto.SubmissionResponse;
import com.divzero.dto.SubmitCodeRequest;
import com.divzero.entity.SubmissionEntity;
import com.divzero.entity.SubmissionStatus;
import com.divzero.event.SubmissionCompletedEvent;
import com.divzero.event.SubmissionCompletionRegistry;
import com.divzero.event.SubmissionCreatedEvent;
import com.divzero.event.SubmissionEventPublisher;
import com.divzero.repository.ProblemRepository;
import com.divzero.repository.SubmissionRepository;
import com.divzero.repository.TestCaseRepository;
import com.divzero.security.AuthenticatedUser;
import com.divzero.service.AuthService;
import com.divzero.service.CacheInvalidationService;
import com.divzero.service.SubmissionRateLimiter;
import com.divzero.service.SubmissionService;
import com.divzero.service.VerdictService;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final AppProperties appProperties;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final AuthService authService;
    private final VerdictService verdictService;
    private final SubmissionEventPublisher eventPublisher;
    private final SubmissionCompletionRegistry completionRegistry;
    private final CacheInvalidationService cacheInvalidationService;
    private final SubmissionRateLimiter submissionRateLimiter;

    @Override
    public ResponseEntity<Object> submitCode(String authorizationHeader, SubmitCodeRequest request) {
        Optional<AuthenticatedUser> principal = authService.authenticate(authorizationHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "User not identified"));
        }

        // The submission owner is always the authenticated principal, never a client-supplied value.
        AuthenticatedUser authenticatedUser = principal.get();

        // Throttle before a row is written, so a rejected attempt leaves no submission behind.
        RateLimitDecision rateLimit = submissionRateLimiter.checkSubmission(authenticatedUser.id());
        if (!rateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.retryAfterSeconds()))
                .body(Map.of(
                    "message", "Too many submissions. Try again shortly.",
                    "retryAfterSeconds", rateLimit.retryAfterSeconds()
                ));
        }

        // Checked up front: an unknown problem used to surface as an opaque foreign-key 400, and a
        // problem with no tests would be judged Accepted for any code that compiles.
        if (!problemRepository.existsById(request.getProblemId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Problem not found"));
        }
        if (!testCaseRepository.existsByProblemId(request.getProblemId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "This problem has no test cases yet"));
        }

        SubmissionEntity submission = createSubmission(authenticatedUser.id(), request);
        CompletableFuture<SubmissionCompletedEvent> completion =
            completionRegistry.register(submission.getId());

        try {
            eventPublisher.publishSubmissionCreated(
                toSubmissionCreatedEvent(submission, authenticatedUser.id(), request)
            );
            SubmissionCompletedEvent completed = completion.get(
                appProperties.getKafka().getSubmitWaitTimeoutMs(),
                TimeUnit.MILLISECONDS
            );
            return ResponseEntity.ok(completed.responseBody());
        } catch (TimeoutException error) {
            completionRegistry.forget(submission.getId());
            log.warn("Timed out waiting for submission completion submissionId={}", submission.getId());
            // 202: the submission is accepted and still being judged. The client polls
            // GET /api/view-submissions for the terminal verdict from here.
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(queuedResponse(submission.getId()));
        } catch (Exception error) {
            completionRegistry.forget(submission.getId());
            log.error("Submission pipeline failed submissionId={}", submission.getId(), error);
            JudgeResult result = verdictService.internalError(null, Collections.emptyList(), error);
            // Persist the terminal status first so the submission is not stranded in QUEUED,
            // then report the failure honestly as a 500 rather than a 200 carrying a verdict.
            handleSubmissionCompleted(
                SubmissionCompletedEvent.fromJudgeResult(submission.getId(), result)
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.responseBody());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getSubmissions(String authorizationHeader, String problemId, String userId) {
        Optional<AuthenticatedUser> principal = authService.authenticate(authorizationHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "User not identified"));
        }

        if (problemId == null || problemId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing problemId"));
        }

        // The userId parameter is deliberately ignored: a caller may only read their own submissions.
        List<SubmissionResponse> submissions = submissionRepository
            .findByProblemIdAndUserIdOrderByTimeDesc(problemId, principal.get().id())
            .stream()
            .map(SubmissionResponse::fromEntity)
            .toList();

        return ResponseEntity.ok(submissions);
    }

    @Override
    @Transactional
    public boolean markRunning(String submissionId) {
        SubmissionEntity submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        if (submission.getStatus().terminal()) {
            log.info(
                "Skipping already terminal submission submissionId={} status={}",
                submission.getId(),
                submission.getStatus()
            );
            return false;
        }
        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setVerdict(SubmissionStatus.RUNNING.displayVerdict());
        submissionRepository.save(submission);
        log.info("Submission running submissionId={}", submission.getId());
        return true;
    }

    /**
     * Intentionally idempotent. Every API instance consumes every completion event (each has its
     * own consumer group), so this runs N times for N instances, and Kafka redelivery can repeat
     * it again. The terminal-status guard below is therefore the first thing to run, ahead of any
     * side effect: no database write and no cache eviction happens on a duplicate. The waiting
     * future is still released on that path, since the instance holding it may well be the one
     * that sees the duplicate.
     */
    @Override
    @Transactional
    public void handleSubmissionCompleted(SubmissionCompletedEvent event) {
        SubmissionEntity submission = submissionRepository.findById(event.submissionId())
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + event.submissionId()));
        if (submission.getStatus().terminal()) {
            log.info(
                "Ignoring duplicate completion event submissionId={} currentStatus={} eventStatus={}",
                submission.getId(),
                submission.getStatus(),
                event.status()
            );
            completionRegistry.complete(event);
            return;
        }
        // Order matters. This probe must run before any setter below dirties the entity:
        // Hibernate flushes pending changes ahead of a query against the same table, so once
        // this submission's own ACCEPTED status is in flight the probe would match the very row
        // we are about to write and always report "already solved".
        boolean alreadySolvedThisProblem = submissionRepository.existsByUserIdAndProblemIdAndStatus(
            submission.getUserId(),
            submission.getProblemId(),
            SubmissionStatus.ACCEPTED
        );

        submission.setStatus(event.status());
        submission.setVerdict(event.verdict());
        submission.setCompileTimeMs(event.compileTimeMs());
        submission.setExecutionTimeMs(event.executionTimeMs());
        submission.setMemoryUsageKb(event.memoryUsageKb());
        submission.setLanguage(event.language());
        submission.setJudgeLogs(event.judgeLogs());
        submissionRepository.save(submission);

        // Only a first solve can move the standings. A non-accepted verdict changes nothing, and
        // re-solving an already-solved problem changes neither the distinct-problem count nor the
        // rank order, so the leaderboard TTL is left to expire on its own in both cases.
        if (event.status() == SubmissionStatus.ACCEPTED && !alreadySolvedThisProblem) {
            cacheInvalidationService.evictLeaderboard();
        }
        completionRegistry.complete(event);
        log.info(
            "Submission completed submissionId={} status={} compileTimeMs={} executionTimeMs={}",
            submission.getId(),
            event.status(),
            event.compileTimeMs(),
            event.executionTimeMs()
        );
    }

    private SubmissionEntity createSubmission(String userId, SubmitCodeRequest request) {
        SubmissionEntity submission = SubmissionEntity.builder()
            .userId(userId)
            .problemId(request.getProblemId())
            .status(SubmissionStatus.QUEUED)
            .verdict(SubmissionStatus.QUEUED.displayVerdict())
            .language("cpp")
            .compileTimeMs(0L)
            .executionTimeMs(0L)
            .memoryUsageKb(null)
            .judgeLogs("")
            .build();

        SubmissionEntity saved = submissionRepository.save(submission);
        log.info(
            "Submission queued submissionId={} userId={} problemId={}",
            saved.getId(),
            saved.getUserId(),
            saved.getProblemId()
        );
        return saved;
    }

    private SubmissionCreatedEvent toSubmissionCreatedEvent(
        SubmissionEntity submission,
        String userId,
        SubmitCodeRequest request
    ) {
        return new SubmissionCreatedEvent(
            submission.getId(),
            userId,
            request.getProblemId(),
            request.getCode(),
            "cpp",
            Instant.now()
        );
    }

    private Map<String, Object> queuedResponse(String submissionId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Queued");
        response.put("status", "Submission queued");
        response.put("submissionId", submissionId);
        return response;
    }
}
