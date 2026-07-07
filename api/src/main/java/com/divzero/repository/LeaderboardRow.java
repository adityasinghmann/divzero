package com.divzero.repository;

public interface LeaderboardRow {

    String getUserId();

    String getName();

    String getUsername();

    Long getAcceptedSubmissions();

    Long getTotalSubmissions();

    Long getTotalExecutionTimeMs();
}
