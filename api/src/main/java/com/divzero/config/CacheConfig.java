package com.divzero.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final AppProperties appProperties;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(
            CacheNames.PROBLEM_LIST,
            defaults.entryTtl(Duration.ofSeconds(appProperties.getCache().getProblemListTtlSeconds()))
        );
        cacheConfigurations.put(
            CacheNames.PROBLEM_DETAILS,
            defaults.entryTtl(Duration.ofSeconds(appProperties.getCache().getProblemDetailsTtlSeconds()))
        );
        cacheConfigurations.put(
            CacheNames.LEADERBOARD,
            defaults.entryTtl(Duration.ofSeconds(appProperties.getCache().getLeaderboardTtlSeconds()))
        );

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new LoggingCacheErrorHandler(true);
    }
}
