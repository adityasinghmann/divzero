package com.divzero.dto;

import com.divzero.entity.SubmissionEntity;
import java.time.LocalDateTime;

public record SubmissionResponse(String id, String verdict, LocalDateTime time) {

    public static SubmissionResponse fromEntity(SubmissionEntity submission) {
        return new SubmissionResponse(
            submission.getId(),
            submission.getVerdict(),
            submission.getTime()
        );
    }
}
