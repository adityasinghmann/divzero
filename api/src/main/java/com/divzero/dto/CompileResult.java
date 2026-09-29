package com.divzero.dto;

public record CompileResult(
    JudgeProcessResult processResult,
    long compileTimeMs
) {

    public boolean successful() {
        return !processResult.timedOut() && processResult.exitCode() == 0;
    }

    public String logs() {
        return """
            [compile]
            exitCode=%d timedOut=%s durationMs=%d
            stdout:
            %s
            stderr:
            %s
            """.formatted(
            processResult.exitCode(),
            processResult.timedOut(),
            compileTimeMs,
            processResult.stdout(),
            processResult.stderr()
        );
    }
}
