package com.divzero.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.divzero.dto.CompileResult;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeProcessResult;
import com.divzero.dto.JudgeResult;
import com.divzero.entity.SubmissionStatus;
import com.divzero.service.impl.VerdictServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Plain JUnit: the verdict rules are pure logic and need no Spring context. */
class VerdictServiceTest {

    private final VerdictService verdictService = new VerdictServiceImpl();

    // ── helpers ──────────────────────────────────────────────────────────────

    private static CompileResult successfulCompile() {
        return new CompileResult(new JudgeProcessResult(0, "", "", false), 42L);
    }

    private static CompileResult failedCompile(String stderr) {
        return new CompileResult(new JudgeProcessResult(1, "", stderr, false), 17L);
    }

    private static ExecutionResult passing(String testId, String expected) {
        return execution(testId, expected, expected, 0, false);
    }

    /** A sample test, which may echo its data back to the client; see hiddenExecution. */
    private static ExecutionResult execution(
        String testId,
        String expected,
        String produced,
        int exitCode,
        boolean timedOut
    ) {
        return new ExecutionResult(testId, "input", expected, produced, "", exitCode, timedOut, 5L, 1024L, true);
    }

    private static ExecutionResult hiddenExecution(String expected, String produced, String stderr, int exitCode) {
        return new ExecutionResult("h1", "secret-input", expected, produced, stderr, exitCode, false, 5L, 1024L, false);
    }

    // ── compilation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("compilation error carries the compiler's stderr and no execution time")
    void compilationError() {
        JudgeResult result = verdictService.compilationError(failedCompile("main.cpp:1: error: boom"));

        assertThat(result.status()).isEqualTo(SubmissionStatus.COMPILATION_ERROR);
        assertThat(result.verdict()).isEqualTo("Compilation Error");
        assertThat(result.responseBody()).containsEntry("message", "Compilation Error");
        assertThat(result.responseBody().get("errorDetails").toString()).contains("error: boom");
        assertThat(result.executionTimeMs()).isZero();
        assertThat(result.compileTimeMs()).isEqualTo(17L);
        // Nothing ran, so nothing was measured - and that must be null, never a sentinel.
        assertThat(result.memoryUsageKb()).isNull();
    }

    @Test
    @DisplayName("compilation error never leaks a raw exception string to the client")
    void compilationErrorBodyHasNoRawException() {
        JudgeResult result = verdictService.compilationError(failedCompile("boom"));

        assertThat(result.responseBody()).doesNotContainKey("error");
    }

    // ── the three-test progression ───────────────────────────────────────────

    @Test
    @DisplayName("wrong answer is reported on the second of three tests, naming that test")
    void wrongAnswerOnSecondOfThreeTests() {
        CompileResult compile = successfulCompile();

        // Test 1 passes: no verdict yet, judging continues.
        List<ExecutionResult> afterFirst = List.of(passing("t1", "1"));
        assertThat(verdictService.evaluateExecution(compile, afterFirst)).isEmpty();

        // Test 2 produces the wrong output: judging stops here.
        List<ExecutionResult> afterSecond = List.of(
            passing("t1", "1"),
            execution("t2", "2", "999", 0, false)
        );
        Optional<JudgeResult> verdict = verdictService.evaluateExecution(compile, afterSecond);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(verdict.get().responseBody().get("message").toString())
            .contains("t2")
            .doesNotContain("t1");
        assertThat(verdict.get().responseBody()).containsEntry("correctOutput", "2");
        assertThat(verdict.get().responseBody()).containsEntry("userOutput", "999");
    }

    @Test
    @DisplayName("all tests passing yields Accepted with the summed execution time")
    void allTestsPass() {
        List<ExecutionResult> executions = List.of(
            passing("t1", "1"),
            passing("t2", "2"),
            passing("t3", "3")
        );

        assertThat(verdictService.evaluateExecution(successfulCompile(), executions)).isEmpty();

        JudgeResult result = verdictService.accepted(successfulCompile(), executions);
        assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result.verdict()).isEqualTo("Accepted");
        assertThat(result.responseBody()).containsEntry("message", "Accepted");
        assertThat(result.executionTimeMs()).isEqualTo(15L);
    }

    // ── failure modes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a timed-out test is Time Limit Exceeded, not Wrong Answer")
    void timeLimitExceeded() {
        List<ExecutionResult> executions = List.of(execution("t1", "1", "", 143, true));

        Optional<JudgeResult> verdict = verdictService.evaluateExecution(successfulCompile(), executions);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(verdict.get().responseBody().get("message").toString()).contains("t1");
    }

    @Test
    @DisplayName("a non-zero exit code is a Runtime Error and reports that code")
    void runtimeErrorOnNonZeroExit() {
        List<ExecutionResult> executions = List.of(execution("t7", "1", "", 139, false));

        Optional<JudgeResult> verdict = verdictService.evaluateExecution(successfulCompile(), executions);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(verdict.get().responseBody()).containsEntry("exitCode", 139);
        assertThat(verdict.get().responseBody().get("message").toString()).contains("t7");
    }

    @Test
    @DisplayName("an OOM kill (exit 137) is Memory Limit Exceeded, not Runtime Error")
    void oomKillIsMemoryLimitExceeded() {
        List<ExecutionResult> executions = List.of(execution("t4", "1", "", 137, false));

        Optional<JudgeResult> verdict = verdictService.evaluateExecution(successfulCompile(), executions);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        assertThat(verdict.get().verdict()).isEqualTo("Memory Limit Exceeded");
        assertThat(verdict.get().status().terminal()).isTrue();
        assertThat(verdict.get().responseBody().get("message").toString())
            .isEqualTo("Memory Limit Exceeded on test t4");
    }

    @Test
    @DisplayName("a host-side timeout kill is still Time Limit Exceeded, even with exit 137")
    void timeoutKillIsNotMemoryLimit() {
        List<ExecutionResult> executions = List.of(execution("t5", "1", "", 137, true));

        Optional<JudgeResult> verdict = verdictService.evaluateExecution(successfulCompile(), executions);

        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("wrong answer on a hidden test reveals neither the test data nor the program output")
    void wrongAnswerOnHiddenTestLeaksNothing() {
        Optional<JudgeResult> verdict = verdictService.evaluateExecution(
            successfulCompile(),
            List.of(hiddenExecution("secret-answer", "secret-input", "", 0))
        );

        assertThat(verdict).isPresent();
        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(verdict.get().responseBody())
            .doesNotContainKeys("input", "correctOutput", "userOutput");
        assertThat(verdict.get().responseBody().toString())
            .doesNotContain("secret-input")
            .doesNotContain("secret-answer");
    }

    @Test
    @DisplayName("runtime error on a hidden test does not echo stderr")
    void runtimeErrorOnHiddenTestHidesStderr() {
        Optional<JudgeResult> verdict = verdictService.evaluateExecution(
            successfulCompile(),
            List.of(hiddenExecution("1", "", "secret-input", 1))
        );

        assertThat(verdict).isPresent();
        assertThat(verdict.get().status()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(verdict.get().responseBody()).containsEntry("exitCode", 1).doesNotContainKey("stderr");
        assertThat(verdict.get().responseBody().toString()).doesNotContain("secret-input");
    }

    @Test
    @DisplayName("an internal error never puts the exception text in the response body")
    void internalErrorHidesTheException() {
        JudgeResult result = verdictService.internalError(
            successfulCompile(),
            List.of(),
            new IllegalStateException("com.divzero.Secret: connection string leaked")
        );

        assertThat(result.status()).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
        assertThat(result.responseBody()).containsEntry("message", "Error while submitting code");
        assertThat(result.responseBody()).doesNotContainKey("error");
        assertThat(result.responseBody().toString()).doesNotContain("connection string leaked");
        // The detail is kept server-side for diagnosis.
        assertThat(result.judgeLogs()).contains("connection string leaked");
    }

    // ── Task 12: output normalisation ────────────────────────────────────────

    @Nested
    @DisplayName("output normalisation")
    class OutputNormalisation {

        private boolean comparesEqual(String expected, String produced) {
            return verdictService
                .evaluateExecution(successfulCompile(), List.of(execution("t1", expected, produced, 0, false)))
                .isEmpty();
        }

        @Test
        @DisplayName("trailing spaces and trailing blank lines are ignored")
        void trailingWhitespaceIgnored() {
            assertThat(comparesEqual("1 2 3", "1 2 3 \n\n")).isTrue();
        }

        @Test
        @DisplayName("genuinely different output still fails")
        void realDifferenceStillFails() {
            assertThat(comparesEqual("ab", "a b")).isFalse();
        }

        @Test
        @DisplayName("trailing whitespace on an interior line is ignored")
        void interiorTrailingWhitespaceIgnored() {
            assertThat(comparesEqual("1\n2\n3", "1 \n2\t\n3")).isTrue();
        }

        @Test
        @DisplayName("CRLF and bare CR line endings are normalised to LF")
        void lineEndingsNormalised() {
            assertThat(comparesEqual("a\nb", "a\r\nb")).isTrue();
            assertThat(comparesEqual("a\nb", "a\rb")).isTrue();
        }

        @Test
        @DisplayName("leading whitespace is significant and is not stripped")
        void leadingWhitespaceIsSignificant() {
            assertThat(comparesEqual("  x", "x")).isFalse();
        }

        @Test
        @DisplayName("an interior blank line is significant")
        void interiorBlankLineIsSignificant() {
            assertThat(comparesEqual("a\nb", "a\n\nb")).isFalse();
        }
    }
}
