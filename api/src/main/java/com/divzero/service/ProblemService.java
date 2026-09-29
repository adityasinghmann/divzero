package com.divzero.service;

import com.divzero.dto.CreateProblemRequest;
import org.springframework.http.ResponseEntity;

public interface ProblemService {

    ResponseEntity<Object> createProblem(String authorizationHeader, CreateProblemRequest request);

    ResponseEntity<Object> getProblems();

    /** @param includeHidden true for admins; everyone else sees sample tests only */
    ResponseEntity<Object> getProblemDetails(String problemId, boolean includeHidden);
}
