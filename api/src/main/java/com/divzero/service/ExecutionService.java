package com.divzero.service;

import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.entity.TestCaseEntity;
import java.io.IOException;

public interface ExecutionService {

    ExecutionResult execute(JudgeWorkspace workspace, TestCaseEntity testCase, int testIndex)
        throws IOException, InterruptedException;
}
