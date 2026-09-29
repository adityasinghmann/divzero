package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.RateLimitDecision;
import com.divzero.service.SubmissionRateLimiter;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed-window submission throttle backed by Redis.
 *
 * <p>The key embeds the window index, so a window expires simply by nobody writing to it again;
 * the TTL set on first increment is only there to stop old keys accumulating.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionRateLimiterImpl implements SubmissionRateLimiter {

    private static final String KEY_PREFIX = "ratelimit:submit:";

    private final AppProperties appProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public RateLimitDecision checkSubmission(String userId) {
        long windowSeconds = appProperties.getRateLimit().getWindowSeconds();
        long limit = appProperties.getRateLimit().getSubmissionsPerWindow();
        long epochSecond = Instant.now().getEpochSecond();
        String key = KEY_PREFIX + userId + ":" + (epochSecond / windowSeconds);

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return RateLimitDecision.allow();
            }
            if (count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count > limit) {
                long retryAfterSeconds = windowSeconds - (epochSecond % windowSeconds);
                log.info(
                    "Submission rate limit exceeded userId={} count={} limit={} windowSeconds={}",
                    userId,
                    count,
                    limit,
                    windowSeconds
                );
                return RateLimitDecision.reject(retryAfterSeconds);
            }
            return RateLimitDecision.allow();
        } catch (RuntimeException error) {
            // Deliberately fail open. Redis being down must not stop people submitting code:
            // losing throttling for a while is a far smaller problem than losing the judge, and
            // every other guard on this endpoint (authentication, validation) still applies.
            log.warn("Rate limiter unavailable, allowing submission userId={}", userId, error);
            return RateLimitDecision.allow();
        }
    }
}
