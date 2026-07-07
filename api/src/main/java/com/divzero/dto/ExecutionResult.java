package com.divzero.dto;

public record ExecutionResult(
    String testId,
    String input,
    String correctOutput,
    String stdout,
    String stderr,
    int exitCode,
    boolean timedOut,
    long executionTimeMs,
    long memoryUsageKb
) {

    public boolean successful() {
        return !timedOut && exitCode == 0;
    }

    public String logs() {
        return """
            [execute test=%s]
            exitCode=%d timedOut=%s durationMs=%d memoryKb=%d
            stdout:
            %s
            stderr:
            %s
            """.formatted(
            testId,
            exitCode,
            timedOut,
            executionTimeMs,
            memoryUsageKb,
            stdout,
            stderr
        );
    }
}
