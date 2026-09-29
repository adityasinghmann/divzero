package com.divzero.dto;

/**
 * Outcome of a rate-limit check.
 *
 * @param allowed            whether the caller may proceed
 * @param retryAfterSeconds  seconds until the current window rolls over; only meaningful when
 *                           the request was rejected
 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0L);
    }

    public static RateLimitDecision reject(long retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds);
    }
}
