package com.divzero.service.impl;

import com.divzero.dto.CompileResult;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeResult;
import com.divzero.entity.SubmissionStatus;
import com.divzero.service.VerdictService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VerdictServiceImpl implements VerdictService {

    private static final String LANGUAGE = "cpp";
    private static final long UNKNOWN_MEMORY_KB = -1L;

    @Override
    public JudgeResult compilationError(CompileResult compileResult) {
        String details = compileResult.processResult().stderr().isBlank()
            ? compileResult.processResult().stdout()
            : compileResult.processResult().stderr();
        if (compileResult.processResult().timedOut()) {
            details = details.isBlank() ? "Compilation timed out." : details + "\nCompilation timed out.";
        }

        return result(
            SubmissionStatus.COMPILATION_ERROR,
            body("message", "Compilation Error", "errorDetails", details),
            compileResult.compileTimeMs(),
            0L,
            UNKNOWN_MEMORY_KB,
            compileResult.logs()
        );
    }

    @Override
    public Optional<JudgeResult> evaluateExecution(
        CompileResult compileResult,
        List<ExecutionResult> executions
    ) {
        if (executions.isEmpty()) {
            return Optional.empty();
        }

        ExecutionResult latest = executions.get(executions.size() - 1);
        if (latest.timedOut() || latest.exitCode() == 124) {
            return Optional.of(result(
                SubmissionStatus.TIME_LIMIT_EXCEEDED,
                body("message", "Time Limit Exceeded on test " + latest.testId()),
                compileResult.compileTimeMs(),
                totalExecutionTime(executions),
                maxMemoryUsage(executions),
                judgeLogs(compileResult, executions)
            ));
        }

        if (!latest.successful()) {
            return Optional.of(result(
                SubmissionStatus.RUNTIME_ERROR,
                body(
                    "message", "Runtime Error on test " + latest.testId(),
                    "exitCode", latest.exitCode(),
                    "stderr", latest.stderr().trim()
                ),
                compileResult.compileTimeMs(),
                totalExecutionTime(executions),
                maxMemoryUsage(executions),
                judgeLogs(compileResult, executions)
            ));
        }

        String cleanUserOutput = latest.stdout().trim();
        String cleanExpectedOutput = latest.correctOutput().trim();
        if (!cleanUserOutput.equals(cleanExpectedOutput)) {
            return Optional.of(result(
                SubmissionStatus.WRONG_ANSWER,
                body(
                    "message", "Failed on test " + latest.testId() + " (Wrong Answer)",
                    "input", latest.input(),
                    "correctOutput", cleanExpectedOutput,
                    "userOutput", cleanUserOutput
                ),
                compileResult.compileTimeMs(),
                totalExecutionTime(executions),
                maxMemoryUsage(executions),
                judgeLogs(compileResult, executions)
            ));
        }

        return Optional.empty();
    }

    @Override
    public JudgeResult accepted(CompileResult compileResult, List<ExecutionResult> executions) {
        return result(
            SubmissionStatus.ACCEPTED,
            body("message", "Accepted", "status", "All tests passed!"),
            compileResult.compileTimeMs(),
            totalExecutionTime(executions),
            maxMemoryUsage(executions),
            judgeLogs(compileResult, executions)
        );
    }

    @Override
    public JudgeResult internalError(
        CompileResult compileResult,
        List<ExecutionResult> executions,
        Exception error
    ) {
        log.error("Internal judge error", error);
        String logs = judgeLogs(compileResult, executions) + "\n[internal]\n" + error;
        return result(
            SubmissionStatus.INTERNAL_ERROR,
            body("message", "Error while submitting code", "error", error.toString()),
            compileResult == null ? 0L : compileResult.compileTimeMs(),
            totalExecutionTime(executions),
            maxMemoryUsage(executions),
            logs
        );
    }

    private JudgeResult result(
        SubmissionStatus status,
        Map<String, Object> responseBody,
        long compileTimeMs,
        long executionTimeMs,
        long memoryUsageKb,
        String judgeLogs
    ) {
        return new JudgeResult(
            status,
            status.displayVerdict(),
            responseBody,
            compileTimeMs,
            executionTimeMs,
            memoryUsageKb,
            LANGUAGE,
            judgeLogs
        );
    }

    private long totalExecutionTime(List<ExecutionResult> executions) {
        return executions.stream()
            .mapToLong(ExecutionResult::executionTimeMs)
            .sum();
    }

    private long maxMemoryUsage(List<ExecutionResult> executions) {
        return executions.stream()
            .mapToLong(ExecutionResult::memoryUsageKb)
            .max()
            .orElse(UNKNOWN_MEMORY_KB);
    }

    private String judgeLogs(CompileResult compileResult, List<ExecutionResult> executions) {
        StringBuilder logs = new StringBuilder();
        if (compileResult != null) {
            logs.append(compileResult.logs());
        }
        executions.forEach(execution -> logs.append('\n').append(execution.logs()));
        return logs.toString();
    }

    private Map<String, Object> body(Object... entries) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            body.put((String) entries[i], entries[i + 1]);
        }
        return body;
    }
}
