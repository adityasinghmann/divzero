package com.divzero.event;

import java.time.Instant;

public record SubmissionCreatedEvent(
    String submissionId,
    String userId,
    String problemId,
    String code,
    String language,
    Instant createdAt
) {
}
