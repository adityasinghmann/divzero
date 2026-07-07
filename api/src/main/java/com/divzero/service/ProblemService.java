package com.divzero.service;

import com.divzero.dto.CreateProblemRequest;
import org.springframework.http.ResponseEntity;

public interface ProblemService {

    ResponseEntity<Object> createProblem(String authorizationHeader, CreateProblemRequest request);

    ResponseEntity<Object> getProblems();

    ResponseEntity<Object> getProblemDetails(String problemId);
}
