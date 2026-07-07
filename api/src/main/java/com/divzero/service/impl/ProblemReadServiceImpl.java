package com.divzero.service.impl;

import com.divzero.config.CacheNames;
import com.divzero.dto.ProblemDetailsResponse;
import com.divzero.dto.ProblemResponse;
import com.divzero.dto.TestResponse;
import com.divzero.entity.ProblemEntity;
import com.divzero.repository.ProblemRepository;
import com.divzero.repository.TestCaseRepository;
import com.divzero.service.ProblemReadService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReadServiceImpl implements ProblemReadService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PROBLEM_LIST, key = "'all'")
    public List<ProblemResponse> getProblemList() {
        log.info("Loading problem list from database");
        return problemRepository.findAll()
            .stream()
            .map(ProblemResponse::fromEntity)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PROBLEM_DETAILS, key = "#problemId", unless = "#result == null")
    public ProblemDetailsResponse getProblemDetails(String problemId) {
        log.info("Loading problem details from database problemId={}", problemId);
        ProblemEntity problem = problemRepository.findById(problemId).orElse(null);
        if (problem == null) {
            return null;
        }

        List<TestResponse> tests = testCaseRepository.findByProblemId(problemId)
            .stream()
            .map(TestResponse::fromEntity)
            .collect(Collectors.toCollection(ArrayList::new));

        return new ProblemDetailsResponse(
            problem.getId(),
            problem.getTitle(),
            problem.getDescription(),
            tests
        );
    }
}
