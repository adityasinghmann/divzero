package com.divzero.worker;

import com.divzero.entity.SubmissionStatus;
import com.divzero.event.SubmissionCompletedEvent;
import com.divzero.event.SubmissionCreatedEvent;
import com.divzero.service.SubmissionService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionDeadLetterListener {

    private final SubmissionService submissionService;

    @KafkaListener(
        topics = "${app.kafka.topics.submission-created-dlt}",
        groupId = "${app.kafka.dead-letter-group-id}"
    )
    public void handleSubmissionCreatedDeadLetter(@Payload SubmissionCreatedEvent event) {
        log.error(
            "SubmissionCreated event reached DLT submissionId={} problemId={}",
            event.submissionId(),
            event.problemId()
        );
        submissionService.handleSubmissionCompleted(
            new SubmissionCompletedEvent(
                event.submissionId(),
                SubmissionStatus.INTERNAL_ERROR,
                SubmissionStatus.INTERNAL_ERROR.displayVerdict(),
                internalErrorResponse(),
                0L,
                0L,
                null,
                event.language(),
                "[dead-letter]\nSubmissionCreated event reached dead-letter topic.",
                Instant.now()
            )
        );
    }

    @KafkaListener(
        topics = "${app.kafka.topics.submission-completed-dlt}",
        groupId = "${app.kafka.dead-letter-group-id}"
    )
    public void handleSubmissionCompletedDeadLetter(@Payload SubmissionCompletedEvent event) {
        log.error(
            "SubmissionCompleted event reached DLT submissionId={} status={}",
            event.submissionId(),
            event.status()
        );
    }

    private Map<String, Object> internalErrorResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Error while submitting code");
        response.put("error", "Submission event reached dead-letter topic");
        return response;
    }
}
