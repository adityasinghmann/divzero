package com.divzero.worker;

import com.divzero.event.SubmissionCompletedEvent;
import com.divzero.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionCompletedListener {

    private final SubmissionService submissionService;

    @KafkaListener(
        topics = "${app.kafka.topics.submission-completed}",
        groupId = "${app.kafka.completion-group-id}"
    )
    public void handleSubmissionCompleted(@Payload SubmissionCompletedEvent event) {
        log.info(
            "Consumed SubmissionCompleted event submissionId={} status={}",
            event.submissionId(),
            event.status()
        );
        submissionService.handleSubmissionCompleted(event);
    }
}
