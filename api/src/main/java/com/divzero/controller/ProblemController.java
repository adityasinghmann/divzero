package com.divzero.controller;

import com.divzero.dto.CreateProblemRequest;
import com.divzero.security.AdminCheck;
import com.divzero.service.ProblemService;
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
public class ProblemController {

    private final ProblemService problemService;

    @PostMapping("/create-problem")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> createProblem(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody CreateProblemRequest request
    ) {
        return problemService.createProblem(authorizationHeader, request);
    }

    @GetMapping("/view-problems")
    public ResponseEntity<Object> getProblems() {
        return problemService.getProblems();
    }

    @GetMapping("/view-problem")
    public ResponseEntity<Object> getProblemDetails(
        @RequestParam(value = "problemId", required = false) String problemId,
        Authentication authentication
    ) {
        return problemService.getProblemDetails(problemId, AdminCheck.isAdmin(authentication));
    }
}
