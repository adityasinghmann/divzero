package com.divzero.repository;

public interface LeaderboardRow {

    String getUserId();

    String getName();

    String getUsername();

    Long getProblemsSolved();

    Long getTotalSubmissions();

    Long getTotalExecutionTimeMs();
}
