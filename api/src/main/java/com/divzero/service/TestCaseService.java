package com.divzero.service;

import com.divzero.dto.CreateTestRequest;
import org.springframework.http.ResponseEntity;

public interface TestCaseService {

    ResponseEntity<Object> createTest(String authorizationHeader, CreateTestRequest request);

    /** @param includeHidden true for admins; everyone else sees sample tests only */
    ResponseEntity<Object> getTests(String problemId, boolean includeHidden);
}
