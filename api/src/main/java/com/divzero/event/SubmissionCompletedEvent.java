package com.divzero.event;

import com.divzero.dto.JudgeResult;
import com.divzero.entity.SubmissionStatus;
import java.time.Instant;
import java.util.Map;

public record SubmissionCompletedEvent(
    String submissionId,
    SubmissionStatus status,
    String verdict,
    Map<String, Object> responseBody,
    long compileTimeMs,
    long executionTimeMs,
    Long memoryUsageKb,
    String language,
    String judgeLogs,
    Instant completedAt
) {

    public static SubmissionCompletedEvent fromJudgeResult(String submissionId, JudgeResult result) {
        return new SubmissionCompletedEvent(
            submissionId,
            result.status(),
            result.verdict(),
            result.responseBody(),
            result.compileTimeMs(),
            result.executionTimeMs(),
            result.memoryUsageKb(),
            result.language(),
            result.judgeLogs(),
            Instant.now()
        );
    }
}
