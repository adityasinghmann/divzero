package com.divzero.service.impl;

import com.divzero.dto.CreateProblemRequest;
import com.divzero.dto.ProblemDetailsResponse;
import com.divzero.dto.ProblemResponse;
import com.divzero.entity.ProblemEntity;
import com.divzero.repository.ProblemRepository;
import com.divzero.service.AuthService;
import com.divzero.service.CacheInvalidationService;
import com.divzero.service.ProblemReadService;
import com.divzero.service.ProblemService;
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
public class ProblemServiceImpl implements ProblemService {

    private final ProblemRepository problemRepository;
    private final AuthService authService;
    private final ProblemReadService problemReadService;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional
    public ResponseEntity<Object> createProblem(String authorizationHeader, CreateProblemRequest request) {
        if (!authService.isAdmin(authorizationHeader)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "User not identified"));
        }

        try {
            ProblemEntity problem = ProblemEntity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .build();
            ProblemResponse response = ProblemResponse.fromEntity(problemRepository.save(problem));
            cacheInvalidationService.evictProblemList();

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ok", "response", response));
        } catch (Exception error) {
            log.warn("Error while creating the problem", error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Error while creating the problem", "error", error.toString()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getProblems() {
        try {
            return ResponseEntity.ok(problemReadService.getProblemList());
        } catch (Exception error) {
            log.warn("Error while finding problems", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error while finding problems"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getProblemDetails(String problemId) {
        if (problemId == null || problemId.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Missing problem Id"));
        }

        try {
            ProblemDetailsResponse problem = problemReadService.getProblemDetails(problemId);
            if (problem == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Problem not found"));
            }
            return ResponseEntity.ok(problem);
        } catch (Exception error) {
            log.warn("Error while finding problem details", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error while finding problem"));
        }
    }
}
