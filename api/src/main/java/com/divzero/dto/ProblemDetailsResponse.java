package com.divzero.dto;

import com.divzero.entity.Difficulty;
import java.util.List;

public record ProblemDetailsResponse(
    String id,
    String title,
    String description,
    long timeLimitMs,
    int memoryLimitMb,
    Difficulty difficulty,
    List<TestResponse> tests
) {

    /** A copy carrying only the sample tests, for callers who must not see hidden ones. */
    public ProblemDetailsResponse samplesOnly() {
        return new ProblemDetailsResponse(
            id,
            title,
            description,
            timeLimitMs,
            memoryLimitMb,
            difficulty,
            tests.stream().filter(TestResponse::sample).toList()
        );
    }
}
