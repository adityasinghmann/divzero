package com.divzero.service;

import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.entity.TestCaseEntity;
import java.io.IOException;

public interface ExecutionService {

    /**
     * @param timeLimitMs wall-clock budget for a single test, resolved per problem by the caller
     * @param memoryLimit Docker-format memory limit for a single test, also resolved per problem
     */
    ExecutionResult execute(
        JudgeWorkspace workspace,
        TestCaseEntity testCase,
        int testIndex,
        long timeLimitMs,
        String memoryLimit
    ) throws IOException, InterruptedException;
}
