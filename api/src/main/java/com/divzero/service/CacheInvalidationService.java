package com.divzero.service;

public interface CacheInvalidationService {

    void evictProblemList();

    void evictProblemDetails(String problemId);

    void evictAllProblemDetails();

    void evictLeaderboard();
}
