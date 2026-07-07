package com.divzero.service;

import com.divzero.dto.CreateTestRequest;
import org.springframework.http.ResponseEntity;

public interface TestCaseService {

    ResponseEntity<Object> createTest(String authorizationHeader, CreateTestRequest request);

    ResponseEntity<Object> getTests(String problemId);
}
