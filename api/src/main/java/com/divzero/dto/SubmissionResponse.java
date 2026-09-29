package com.divzero.dto;

import com.divzero.entity.SubmissionEntity;
import java.time.Instant;
import java.time.ZoneId;

/**
 * {@code time} is an Instant so it serializes with an explicit UTC offset ("...Z"). A bare
 * LocalDateTime carries no zone, and browsers parse such a string as their own local time,
 * which shifted every submission time by the viewer's UTC offset.
 */
public record SubmissionResponse(String id, String verdict, Instant time) {

    public static SubmissionResponse fromEntity(SubmissionEntity submission) {
        return new SubmissionResponse(
            submission.getId(),
            submission.getVerdict(),
            toInstant(submission)
        );
    }

    // @CreationTimestamp fills the LocalDateTime from the JVM clock in the JVM's default zone,
    // and Hibernate round-trips it in that same zone, so that is the zone to resolve it in.
    private static Instant toInstant(SubmissionEntity submission) {
        return submission.getTime() == null
            ? null
            : submission.getTime().atZone(ZoneId.systemDefault()).toInstant();
    }
}
