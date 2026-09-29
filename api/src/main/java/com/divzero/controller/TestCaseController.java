package com.divzero.controller;

import com.divzero.dto.CreateTestRequest;
import com.divzero.security.AdminCheck;
import com.divzero.service.TestCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping("/create-test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> createTest(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody CreateTestRequest request
    ) {
        return testCaseService.createTest(authorizationHeader, request);
    }

    @GetMapping("/view-tests")
    public ResponseEntity<Object> getTests(
        @RequestParam(value = "problemId", required = false) String problemId,
        Authentication authentication
    ) {
        return testCaseService.getTests(problemId, AdminCheck.isAdmin(authentication));
    }
}
