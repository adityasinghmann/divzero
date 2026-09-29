package com.divzero.dto;

import com.divzero.entity.Difficulty;
import com.divzero.entity.ProblemEntity;

public record ProblemResponse(
    String id,
    String title,
    String description,
    long timeLimitMs,
    int memoryLimitMb,
    Difficulty difficulty
) {

    public static ProblemResponse fromEntity(ProblemEntity problem) {
        return new ProblemResponse(
            problem.getId(),
            problem.getTitle(),
            problem.getDescription(),
            problem.getTimeLimitMs() == null
                ? ProblemEntity.DEFAULT_TIME_LIMIT_MS
                : problem.getTimeLimitMs(),
            problem.getMemoryLimitMb() == null
                ? ProblemEntity.DEFAULT_MEMORY_LIMIT_MB
                : problem.getMemoryLimitMb(),
            problem.getDifficulty() == null ? Difficulty.MEDIUM : problem.getDifficulty()
        );
    }
}
