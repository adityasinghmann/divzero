package com.divzero.dto;

public record LeaderboardEntryResponse(
    int rank,
    String userId,
    String name,
    String username,
    long problemsSolved,
    long totalSubmissions,
    long totalExecutionTimeMs
) {
}
