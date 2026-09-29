package com.divzero.service.impl;

import com.divzero.dto.CompileResult;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeResult;
import com.divzero.entity.SubmissionStatus;
import com.divzero.service.VerdictService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VerdictServiceImpl implements VerdictService {

    private static final String LANGUAGE = "cpp";
    private static final int OOM_KILLED_EXIT_CODE = 137;

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
            null,
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

        // 137 = 128 + SIGKILL. Inside the sandbox the only sender of SIGKILL is the kernel OOM
        // killer enforcing the container's memory limit (a host-side timeout kill is reported as
        // timedOut and handled above), so this is the problem's memory limit, not a crash.
        if (latest.exitCode() == OOM_KILLED_EXIT_CODE) {
            return Optional.of(result(
                SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                body("message", "Memory Limit Exceeded on test " + latest.testId()),
                compileResult.compileTimeMs(),
                totalExecutionTime(executions),
                maxMemoryUsage(executions),
                judgeLogs(compileResult, executions)
            ));
        }

        if (!latest.successful()) {
            Map<String, Object> runtimeErrorBody = body(
                "message", "Runtime Error on test " + latest.testId(),
                "exitCode", latest.exitCode()
            );
            // stderr is only echoed for samples: a submission could print a hidden test's input
            // to stderr and crash on purpose to exfiltrate it.
            if (latest.sample()) {
                runtimeErrorBody.put("stderr", latest.stderr().trim());
            }
            return Optional.of(result(
                SubmissionStatus.RUNTIME_ERROR,
                runtimeErrorBody,
                compileResult.compileTimeMs(),
                totalExecutionTime(executions),
                maxMemoryUsage(executions),
                judgeLogs(compileResult, executions)
            ));
        }

        String cleanUserOutput = normalizeOutput(latest.stdout());
        String cleanExpectedOutput = normalizeOutput(latest.correctOutput());
        if (!cleanUserOutput.equals(cleanExpectedOutput)) {
            Map<String, Object> wrongAnswerBody = body(
                "message", "Failed on test " + latest.testId() + " (Wrong Answer)"
            );
            // A hidden test's data never leaves the server, including the program's own output,
            // which could simply echo the input back.
            if (latest.sample()) {
                wrongAnswerBody.put("input", latest.input());
                wrongAnswerBody.put("correctOutput", cleanExpectedOutput);
                wrongAnswerBody.put("userOutput", cleanUserOutput);
            }
            return Optional.of(result(
                SubmissionStatus.WRONG_ANSWER,
                wrongAnswerBody,
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
            // The exception stays in judgeLogs (server side); it is never handed to the client.
            body("message", "Error while submitting code"),
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
        Long memoryUsageKb,
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

    /**
     * Makes output comparison insensitive to the differences that carry no meaning: line-ending
     * style, trailing spaces at the end of a line, and blank lines at the end of the output.
     *
     * <p>Leading whitespace is deliberately preserved. Indentation can be part of a correct
     * answer (trees, grids, formatted tables), so stripping it would accept wrong output.
     */
    private String normalizeOutput(String raw) {
        if (raw == null) {
            return "";
        }

        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].stripTrailing();
        }

        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }

        return String.join("\n", Arrays.copyOf(lines, end));
    }

    private long totalExecutionTime(List<ExecutionResult> executions) {
        return executions.stream()
            .mapToLong(ExecutionResult::executionTimeMs)
            .sum();
    }

    /** Null when no test reported a reading, so an unmeasured run is never reported as a number. */
    private Long maxMemoryUsage(List<ExecutionResult> executions) {
        return executions.stream()
            .map(ExecutionResult::memoryUsageKb)
            .filter(Objects::nonNull)
            .max(Long::compare)
            .orElse(null);
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
