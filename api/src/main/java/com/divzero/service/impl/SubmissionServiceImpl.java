package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.JudgeResult;
import com.divzero.dto.SubmissionResponse;
import com.divzero.dto.SubmitCodeRequest;
import com.divzero.entity.SubmissionEntity;
import com.divzero.entity.SubmissionStatus;
import com.divzero.event.SubmissionCompletedEvent;
import com.divzero.event.SubmissionCompletionRegistry;
import com.divzero.event.SubmissionCreatedEvent;
import com.divzero.event.SubmissionEventPublisher;
import com.divzero.repository.SubmissionRepository;
import com.divzero.service.AuthService;
import com.divzero.service.CacheInvalidationService;
import com.divzero.service.SubmissionService;
import com.divzero.service.VerdictService;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AuthService authService;
    private final VerdictService verdictService;
    private final SubmissionEventPublisher eventPublisher;
    private final SubmissionCompletionRegistry completionRegistry;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    public ResponseEntity<Object> submitCode(String authorizationHeader, SubmitCodeRequest request) {
        if (authService.authenticate(authorizationHeader).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "User not identified"));
        }

        SubmissionEntity submission = createSubmission(request);
        CompletableFuture<SubmissionCompletedEvent> completion =
            completionRegistry.register(submission.getId());

        try {
            eventPublisher.publishSubmissionCreated(toSubmissionCreatedEvent(submission, request));
            SubmissionCompletedEvent completed = completion.get(
                appProperties.getKafka().getSubmitWaitTimeoutMs(),
                TimeUnit.MILLISECONDS
            );
            return ResponseEntity.ok(completed.responseBody());
        } catch (TimeoutException error) {
            completionRegistry.forget(submission.getId());
            log.warn("Timed out waiting for submission completion submissionId={}", submission.getId());
            return ResponseEntity.ok(queuedResponse(submission.getId()));
        } catch (Exception error) {
            completionRegistry.forget(submission.getId());
            log.error("Submission pipeline failed submissionId={}", submission.getId(), error);
            JudgeResult result = verdictService.internalError(null, Collections.emptyList(), error);
            handleSubmissionCompleted(
                SubmissionCompletedEvent.fromJudgeResult(submission.getId(), result)
            );
            return ResponseEntity.ok(result.responseBody());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getSubmissions(String authorizationHeader, String problemId, String userId) {
        if (authService.authenticate(authorizationHeader).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "User not identified"));
        }

        if (problemId == null || problemId.isBlank() || userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing problemId or userId"));
        }

        List<SubmissionResponse> submissions = submissionRepository
            .findByProblemIdAndUserIdOrderByTimeDesc(problemId, userId)
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
        submission.setStatus(event.status());
        submission.setVerdict(event.verdict());
        submission.setCompileTimeMs(event.compileTimeMs());
        submission.setExecutionTimeMs(event.executionTimeMs());
        submission.setMemoryUsageKb(event.memoryUsageKb());
        submission.setLanguage(event.language());
        submission.setJudgeLogs(event.judgeLogs());
        submissionRepository.save(submission);
        cacheInvalidationService.evictLeaderboard();
        completionRegistry.complete(event);
        log.info(
            "Submission completed submissionId={} status={} compileTimeMs={} executionTimeMs={}",
            submission.getId(),
            event.status(),
            event.compileTimeMs(),
            event.executionTimeMs()
        );
    }

    private SubmissionEntity createSubmission(SubmitCodeRequest request) {
        SubmissionEntity submission = SubmissionEntity.builder()
            .userId(request.getUserId())
            .problemId(request.getProblemId())
            .status(SubmissionStatus.QUEUED)
            .verdict(SubmissionStatus.QUEUED.displayVerdict())
            .language("cpp")
            .compileTimeMs(0L)
            .executionTimeMs(0L)
            .memoryUsageKb(-1L)
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
        SubmitCodeRequest request
    ) {
        return new SubmissionCreatedEvent(
            submission.getId(),
            request.getUserId(),
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
