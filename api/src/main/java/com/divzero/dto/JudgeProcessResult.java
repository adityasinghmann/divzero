package com.divzero.dto;

public record JudgeProcessResult(
    int exitCode,
    String stdout,
    String stderr,
    boolean timedOut
) {
}
