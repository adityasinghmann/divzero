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
    /** Peak resident memory of the sandbox for this test, or null when it could not be read. */
    Long memoryUsageKb,
    /** Whether the test is a public sample; hidden tests must not have their data echoed back. */
    boolean sample
) {

    public boolean successful() {
        return !timedOut && exitCode == 0;
    }

    public String logs() {
        return """
            [execute test=%s]
            exitCode=%d timedOut=%s durationMs=%d memoryKb=%s
            stdout:
            %s
            stderr:
            %s
            """.formatted(
            testId,
            exitCode,
            timedOut,
            executionTimeMs,
            memoryUsageKb == null ? "unknown" : memoryUsageKb.toString(),
            stdout,
            stderr
        );
    }
}
