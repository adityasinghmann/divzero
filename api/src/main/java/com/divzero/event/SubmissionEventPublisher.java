package com.divzero.event;

public interface SubmissionEventPublisher {

    void publishSubmissionCreated(SubmissionCreatedEvent event);

    void publishSubmissionCompleted(SubmissionCompletedEvent event);
}
