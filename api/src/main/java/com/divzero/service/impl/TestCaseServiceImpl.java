package com.divzero.service.impl;

import com.divzero.dto.CreateTestRequest;
import com.divzero.dto.TestResponse;
import com.divzero.entity.TestCaseEntity;
import com.divzero.repository.TestCaseRepository;
import com.divzero.service.AuthService;
import com.divzero.service.CacheInvalidationService;
import com.divzero.service.TestCaseService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final AuthService authService;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional
    public ResponseEntity<Object> createTest(String authorizationHeader, CreateTestRequest request) {
        if (!authService.isAdmin(authorizationHeader)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "User not identified"));
        }

        try {
            TestCaseEntity test = TestCaseEntity.builder()
                .problemId(request.getProblemId())
                .input(request.getInput())
                .correctOutput(request.getCorrectOutput())
                .build();
            TestResponse response = TestResponse.fromEntity(testCaseRepository.save(test));
            cacheInvalidationService.evictProblemDetails(request.getProblemId());
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ok", "res", response));
        } catch (Exception error) {
            log.warn("Error while creating the test", error);
            return ResponseEntity.ok(Map.of("message", "Error while creating the test", "error", error.toString()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getTests(String problemId) {
        if (problemId == null || problemId.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Missing problem Id"));
        }

        try {
            List<TestResponse> tests = testCaseRepository.findByProblemId(problemId)
                .stream()
                .map(TestResponse::fromEntity)
                .toList();
            return ResponseEntity.ok(tests);
        } catch (Exception error) {
            log.warn("Error while getting tests", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error while getting tests"));
        }
    }
}
