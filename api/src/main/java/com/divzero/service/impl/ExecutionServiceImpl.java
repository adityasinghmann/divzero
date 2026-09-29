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
import java.nio.file.Path;
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

    /** Head-room over the test's own time limit before the host gives up on the container. */
    private static final long PROCESS_GRACE_MS = 3000L;

    private static final int GNU_TIMEOUT_EXIT_CODE = 124;
    private static final int SIGTERM_EXIT_CODE = 143;

    private static final String CGROUP_V2_PEAK = "/sys/fs/cgroup/memory.peak";
    private static final String CGROUP_V1_PEAK = "/sys/fs/cgroup/memory/memory.max_usage_in_bytes";

    private final AppProperties appProperties;
    private final DockerProcessService dockerProcessService;

    @Override
    public ExecutionResult execute(
        JudgeWorkspace workspace,
        TestCaseEntity testCase,
        int testIndex,
        long timeLimitMs,
        String memoryLimit
    ) throws IOException, InterruptedException {
        Files.writeString(
            workspace.directory().resolve(inputFile(testIndex)),
            testCase.getInput(),
            StandardCharsets.UTF_8
        );

        String containerName = "divzero_run_" + safeContainerSuffix(workspace.submissionId()) + "_" + testIndex;
        List<String> command = dockerProcessService.dockerRunBase(
            workspace.directory(),
            containerName,
            memoryLimit
        );
        // Rounded up: busybox timeout takes whole seconds, and rounding down would enforce a
        // stricter limit than the problem declares.
        long timeoutSeconds = Math.max(1L, (timeLimitMs + 999L) / 1000L);
        command.add(appProperties.getJudge().getDockerImage());
        command.add("sh");
        command.add("-c");
        // The program's exit code is captured before the memory probe runs and re-raised at the
        // end, so measuring cannot mask a runtime error or a timeout. Reading the cgroup from
        // inside the container is the only option here: docker inspect reports no memory usage,
        // and a container's cgroup is torn down by the kernel the moment its last process exits,
        // so nothing on the host can read it afterwards. cgroup v2 exposes memory.peak; the v1
        // path is listed as a fallback and whichever exists first is used.
        command.add("timeout " + timeoutSeconds + "s /code/" + JudgeWorkspace.BINARY_FILE
            + " < /code/" + inputFile(testIndex)
            + "; __rc=$?; cat " + CGROUP_V2_PEAK + " " + CGROUP_V1_PEAK
            + " 2>/dev/null | head -1 > /code/" + memoryFile(testIndex)
            + "; exit $__rc");

        long started = System.nanoTime();
        JudgeProcessResult processResult = dockerProcessService.run(
            command,
            Duration.ofMillis(timeLimitMs + PROCESS_GRACE_MS),
            containerName
        );
        long durationMs = elapsedMillis(started);
        boolean timedOut = processResult.timedOut() || isContainerTimeout(processResult.exitCode());
        Long memoryUsageKb = readPeakMemoryKb(workspace, testIndex);

        log.info(
            "Execution finished submissionId={} testId={} exitCode={} timedOut={} durationMs={} memoryKb={}",
            workspace.submissionId(),
            testCase.getId(),
            processResult.exitCode(),
            timedOut,
            durationMs,
            memoryUsageKb,
            testCase.isSample()
        );

        return new ExecutionResult(
            testCase.getId(),
            testCase.getInput(),
            testCase.getCorrectOutput(),
            processResult.stdout(),
            processResult.stderr(),
            processResult.exitCode(),
            timedOut,
            durationMs,
            memoryUsageKb,
            testCase.isSample()
        );
    }

    /**
     * The in-container limit is enforced by busybox timeout, which terminates the program with
     * SIGTERM and so reports 128 + 15 = 143 rather than GNU timeout's 124. Both are treated as a
     * time limit hit; nothing else in this sandbox sends SIGTERM to the submitted program.
     */
    private boolean isContainerTimeout(int exitCode) {
        return exitCode == GNU_TIMEOUT_EXIT_CODE || exitCode == SIGTERM_EXIT_CODE;
    }

    /**
     * Reads back the peak the sandbox wrote on its way out. Returns null — never a sentinel —
     * when the value is absent or unparseable, so callers can tell "not measured" from a real
     * reading. The figure is the whole cgroup's high-water mark, so it includes the few hundred
     * kilobytes used by the shell wrapping the submitted program.
     */
    private Long readPeakMemoryKb(JudgeWorkspace workspace, int testIndex) {
        Path peakFile = workspace.directory().resolve(memoryFile(testIndex));
        try {
            if (!Files.exists(peakFile)) {
                return null;
            }
            String raw = Files.readString(peakFile, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return null;
            }
            long bytes = Long.parseLong(raw);
            return bytes < 0 ? null : bytes / 1024L;
        } catch (IOException | NumberFormatException error) {
            log.debug("Could not read peak memory for test {} of {}", testIndex, workspace.submissionId());
            return null;
        }
    }

    private String memoryFile(int testIndex) {
        return "mem_" + testIndex + ".txt";
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
