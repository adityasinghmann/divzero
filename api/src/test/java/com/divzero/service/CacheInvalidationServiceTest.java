package com.divzero.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.divzero.config.CacheNames;
import com.divzero.service.impl.CacheInvalidationServiceImpl;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Plain JUnit: drives the transaction synchronization callbacks by hand. */
class CacheInvalidationServiceTest {

    /** A cache standing in for Redis being unreachable. */
    private static final class BrokenCache extends ConcurrentMapCache {
        BrokenCache(String name) {
            super(name);
        }

        @Override
        public void clear() {
            throw new IllegalStateException("redis down");
        }

        @Override
        public void evict(Object key) {
            throw new IllegalStateException("redis down");
        }
    }

    private static CacheManager managerOf(Cache cache) {
        return new CacheManager() {
            @Override
            public Cache getCache(String name) {
                return new TransactionAwareCacheDecorator(cache);
            }

            @Override
            public Collection<String> getCacheNames() {
                return List.of(cache.getName());
            }
        };
    }

    private static void commit() {
        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("inside a transaction, eviction waits for commit")
    void evictionIsDeferredUntilCommit() {
        ConcurrentMapCache cache = new ConcurrentMapCache(CacheNames.PROBLEM_DETAILS);
        cache.put("p1", "stale");
        CacheInvalidationService service = new CacheInvalidationServiceImpl(managerOf(cache));

        TransactionSynchronizationManager.initSynchronization();
        service.evictProblemDetails("p1");
        assertThat(cache.get("p1")).isNotNull();

        commit();
        assertThat(cache.get("p1")).isNull();
    }

    @Test
    @DisplayName("a cache failure after commit is swallowed, so a committed write never reports 500")
    void failureAfterCommitDoesNotPropagate() {
        CacheInvalidationService service =
            new CacheInvalidationServiceImpl(managerOf(new BrokenCache(CacheNames.PROBLEM_LIST)));

        TransactionSynchronizationManager.initSynchronization();
        service.evictProblemList();
        service.evictProblemDetails("p1");
        service.evictLeaderboard();

        assertThatCode(CacheInvalidationServiceTest::commit).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("outside a transaction, eviction runs immediately and still never throws")
    void noTransactionEvictsImmediately() {
        ConcurrentMapCache cache = new ConcurrentMapCache(CacheNames.LEADERBOARD);
        cache.put("global", "stale");
        new CacheInvalidationServiceImpl(managerOf(cache)).evictLeaderboard();
        assertThat(cache.get("global")).isNull();

        assertThatCode(() -> new CacheInvalidationServiceImpl(managerOf(new BrokenCache("x"))).evictLeaderboard())
            .doesNotThrowAnyException();
    }
}
