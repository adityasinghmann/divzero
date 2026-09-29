package com.divzero.controller;

import com.divzero.dto.SubmitCodeRequest;
import com.divzero.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/submit-code")
    public ResponseEntity<Object> submitCode(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody SubmitCodeRequest request
    ) {
        return submissionService.submitCode(authorizationHeader, request);
    }

    @GetMapping("/view-submissions")
    public ResponseEntity<Object> getSubmissions(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestParam(value = "problemId", required = false) String problemId
    ) {
        // The owner is resolved from the token inside the service; no userId is accepted from the client.
        return submissionService.getSubmissions(authorizationHeader, problemId, null);
    }
}
