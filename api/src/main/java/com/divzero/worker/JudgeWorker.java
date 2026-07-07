package com.divzero.worker;

import com.divzero.dto.JudgeResult;
import com.divzero.event.SubmissionCompletedEvent;
import com.divzero.event.SubmissionCreatedEvent;
import com.divzero.event.SubmissionEventPublisher;
import com.divzero.service.JudgeService;
import com.divzero.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeWorker {

    private final SubmissionService submissionService;
    private final JudgeService judgeService;
    private final SubmissionEventPublisher eventPublisher;

    @KafkaListener(
        topics = "${app.kafka.topics.submission-created}",
        groupId = "${app.kafka.judge-group-id}"
    )
    public void handleSubmissionCreated(@Payload SubmissionCreatedEvent event) {
        log.info(
            "Consumed SubmissionCreated event submissionId={} problemId={}",
            event.submissionId(),
            event.problemId()
        );
        if (!submissionService.markRunning(event.submissionId())) {
            return;
        }
        JudgeResult result = judgeService.judge(event);
        eventPublisher.publishSubmissionCompleted(
            SubmissionCompletedEvent.fromJudgeResult(event.submissionId(), result)
        );
    }
}
