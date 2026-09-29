package com.divzero.service;

import com.divzero.dto.CompileResult;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeResult;
import java.util.List;
import java.util.Optional;

public interface VerdictService {

    JudgeResult compilationError(CompileResult compileResult);

    Optional<JudgeResult> evaluateExecution(CompileResult compileResult, List<ExecutionResult> executions);

    JudgeResult accepted(CompileResult compileResult, List<ExecutionResult> executions);

    JudgeResult internalError(CompileResult compileResult, List<ExecutionResult> executions, Exception error);
}
