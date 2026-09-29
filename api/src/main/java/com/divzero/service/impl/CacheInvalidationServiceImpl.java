package com.divzero.service.impl;

import com.divzero.config.CacheNames;
import com.divzero.service.CacheInvalidationService;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts cache entries after the surrounding transaction commits, and never lets a cache failure
 * reach the caller.
 *
 * <p>The cache manager is transaction-aware, so its caches already defer evictions to
 * afterCommit. But Spring propagates exceptions thrown from afterCommit to the caller, so with
 * Redis down a write that had already committed would still answer 500. This class therefore
 * unwraps the decorator and registers its own synchronization, with the failure caught and
 * logged: a stale entry for one TTL is far better than reporting a successful write as failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationServiceImpl implements CacheInvalidationService {

    private final CacheManager cacheManager;

    @Override
    public void evictProblemList() {
        afterCommit(CacheNames.PROBLEM_LIST, "all entries", Cache::clear);
    }

    @Override
    public void evictProblemDetails(String problemId) {
        if (problemId == null || problemId.isBlank()) {
            return;
        }
        afterCommit(CacheNames.PROBLEM_DETAILS, "key=" + problemId, cache -> cache.evict(problemId));
    }

    @Override
    public void evictAllProblemDetails() {
        afterCommit(CacheNames.PROBLEM_DETAILS, "all entries", Cache::clear);
    }

    @Override
    public void evictLeaderboard() {
        afterCommit(CacheNames.LEADERBOARD, "all entries", Cache::clear);
    }

    private void afterCommit(String cacheName, String what, Consumer<Cache> action) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        Cache target = cache instanceof TransactionAwareCacheDecorator decorator
            ? decorator.getTargetCache()
            : cache;

        Runnable evict = () -> {
            try {
                action.accept(target);
                log.info("Evicted cache={} {}", cacheName, what);
            } catch (RuntimeException error) {
                log.warn("Failed to evict cache={} {}", cacheName, what, error);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
