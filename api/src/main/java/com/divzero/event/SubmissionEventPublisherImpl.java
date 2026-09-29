package com.divzero.event;

import com.divzero.config.AppProperties;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionEventPublisherImpl implements SubmissionEventPublisher {

    private final AppProperties appProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishSubmissionCreated(SubmissionCreatedEvent event) {
        publish(appProperties.getKafka().getTopics().getSubmissionCreated(), event.submissionId(), event);
    }

    @Override
    public void publishSubmissionCompleted(SubmissionCompletedEvent event) {
        publish(appProperties.getKafka().getTopics().getSubmissionCompleted(), event.submissionId(), event);
    }

    private void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event)
                .get(appProperties.getKafka().getPublishTimeoutMs(), TimeUnit.MILLISECONDS);
            log.info("Published Kafka event topic={} key={}", topic, key);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException("Failed to publish Kafka event to " + topic, error);
        }
    }
}
