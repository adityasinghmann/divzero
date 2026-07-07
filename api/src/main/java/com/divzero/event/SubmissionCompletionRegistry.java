package com.divzero.event;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class SubmissionCompletionRegistry {

    private final ConcurrentMap<String, CompletableFuture<SubmissionCompletedEvent>> waiting =
        new ConcurrentHashMap<>();

    public CompletableFuture<SubmissionCompletedEvent> register(String submissionId) {
        CompletableFuture<SubmissionCompletedEvent> future = new CompletableFuture<>();
        waiting.put(submissionId, future);
        return future;
    }

    public void complete(SubmissionCompletedEvent event) {
        Optional.ofNullable(waiting.remove(event.submissionId()))
            .ifPresent(future -> future.complete(event));
    }

    public void fail(String submissionId, Throwable error) {
        Optional.ofNullable(waiting.remove(submissionId))
            .ifPresent(future -> future.completeExceptionally(error));
    }

    public void forget(String submissionId) {
        waiting.remove(submissionId);
    }
}
