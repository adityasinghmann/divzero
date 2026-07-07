package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeProcessResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.entity.TestCaseEntity;
import com.divzero.service.DockerProcessService;
import com.divzero.service.ExecutionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionServiceImpl implements ExecutionService {

    private static final long UNKNOWN_MEMORY_KB = -1L;

    private final AppProperties appProperties;
    private final DockerProcessService dockerProcessService;

    @Override
    public ExecutionResult execute(JudgeWorkspace workspace, TestCaseEntity testCase, int testIndex)
        throws IOException, InterruptedException {
        Files.writeString(
            workspace.directory().resolve(inputFile(testIndex)),
            testCase.getInput(),
            StandardCharsets.UTF_8
        );

        String containerName = "divzero_run_" + safeContainerSuffix(workspace.submissionId()) + "_" + testIndex;
        List<String> command = dockerProcessService.dockerRunBase(workspace.directory(), containerName);
        long timeoutSeconds = Math.max(1L, appProperties.getJudge().getExecutionTimeoutMs() / 1000L);
        command.add(appProperties.getJudge().getDockerImage());
        command.add("sh");
        command.add("-c");
        command.add("timeout " + timeoutSeconds + "s /code/" + JudgeWorkspace.BINARY_FILE
            + " < /code/" + inputFile(testIndex));

        long started = System.nanoTime();
        JudgeProcessResult processResult = dockerProcessService.run(
            command,
            Duration.ofMillis(appProperties.getJudge().getExecutionTimeoutMs() + 3000L),
            containerName
        );
        long durationMs = elapsedMillis(started);

        log.info(
            "Execution finished submissionId={} testId={} exitCode={} timedOut={} durationMs={}",
            workspace.submissionId(),
            testCase.getId(),
            processResult.exitCode(),
            processResult.timedOut(),
            durationMs
        );

        return new ExecutionResult(
            testCase.getId(),
            testCase.getInput(),
            testCase.getCorrectOutput(),
            processResult.stdout(),
            processResult.stderr(),
            processResult.exitCode(),
            processResult.timedOut(),
            durationMs,
            UNKNOWN_MEMORY_KB
        );
    }

    private String inputFile(int testIndex) {
        return "input_" + testIndex + ".txt";
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String safeContainerSuffix(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
