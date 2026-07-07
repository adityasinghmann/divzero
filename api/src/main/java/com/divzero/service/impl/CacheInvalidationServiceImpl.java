package com.divzero.service.impl;

import com.divzero.config.CacheNames;
import com.divzero.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationServiceImpl implements CacheInvalidationService {

    private final CacheManager cacheManager;

    @Override
    public void evictProblemList() {
        clear(CacheNames.PROBLEM_LIST);
    }

    @Override
    public void evictProblemDetails(String problemId) {
        Cache cache = cacheManager.getCache(CacheNames.PROBLEM_DETAILS);
        if (cache != null && problemId != null && !problemId.isBlank()) {
            try {
                cache.evict(problemId);
                log.info("Evicted cache={} key={}", CacheNames.PROBLEM_DETAILS, problemId);
            } catch (RuntimeException error) {
                log.warn(
                    "Failed to evict cache={} key={}",
                    CacheNames.PROBLEM_DETAILS,
                    problemId,
                    error
                );
            }
        }
    }

    @Override
    public void evictAllProblemDetails() {
        clear(CacheNames.PROBLEM_DETAILS);
    }

    @Override
    public void evictLeaderboard() {
        clear(CacheNames.LEADERBOARD);
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            try {
                cache.clear();
                log.info("Cleared cache={}", cacheName);
            } catch (RuntimeException error) {
                log.warn("Failed to clear cache={}", cacheName, error);
            }
        }
    }
}
