package com.divzero.service;

import com.divzero.dto.SubmitCodeRequest;
import com.divzero.event.SubmissionCompletedEvent;
import org.springframework.http.ResponseEntity;

public interface SubmissionService {

    ResponseEntity<Object> submitCode(String authorizationHeader, SubmitCodeRequest request);

    ResponseEntity<Object> getSubmissions(String authorizationHeader, String problemId, String userId);

    boolean markRunning(String submissionId);

    void handleSubmissionCompleted(SubmissionCompletedEvent event);
}
