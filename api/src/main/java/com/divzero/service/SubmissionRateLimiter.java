package com.divzero.service;

import com.divzero.dto.RateLimitDecision;

public interface SubmissionRateLimiter {

    /**
     * Records one submission attempt for the user and reports whether it may proceed.
     */
    RateLimitDecision checkSubmission(String userId);
}
