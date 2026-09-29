package com.divzero.dto;

import com.divzero.entity.SubmissionStatus;
import java.util.Map;

public record JudgeResult(
    SubmissionStatus status,
    String verdict,
    Map<String, Object> responseBody,
    long compileTimeMs,
    long executionTimeMs,
    /** Peak sandbox memory across the tests that ran, or null when nothing was measured. */
    Long memoryUsageKb,
    String language,
    String judgeLogs
) {
}
