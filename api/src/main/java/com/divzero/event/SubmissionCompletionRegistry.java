package com.divzero.event;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Parks the HTTP thread of a synchronous submit until the matching completion event arrives.
 *
 * <p>This is an in-process map, so the optimisation only pays off while the instance that
 * registered the future is still alive: if it restarts or is rescheduled mid-judge, the future
 * dies with it and nothing here can deliver the verdict. That is deliberate rather than fixed —
 * the durable path is client polling of GET /api/view-submissions, which is now the documented
 * fallback and takes over automatically once the submit wait times out and returns 202.
 */
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
