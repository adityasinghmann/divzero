package com.divzero.dto;

public record LeaderboardEntryResponse(
    int rank,
    String userId,
    String name,
    String username,
    long acceptedSubmissions,
    long totalSubmissions,
    long totalExecutionTimeMs
) {
}
