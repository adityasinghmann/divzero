package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.CompileResult;
import com.divzero.dto.JudgeProcessResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.service.CompilerService;
import com.divzero.service.DockerProcessService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompilerServiceImpl implements CompilerService {

    private final AppProperties appProperties;
    private final DockerProcessService dockerProcessService;

    @Override
    public CompileResult compile(JudgeWorkspace workspace) throws IOException, InterruptedException {
        String containerName = "divzero_compile_" + safeContainerSuffix(workspace.submissionId());
        // Compilation deliberately uses the global judge memory budget, not the per-problem
        // execution memory limit: g++ needs far more headroom than the submitted program does,
        // and a low per-problem limit must not turn every submission into a compile failure.
        List<String> command = dockerProcessService.dockerRunBase(
            workspace.directory(),
            containerName,
            appProperties.getJudge().getMemory()
        );
        command.add(appProperties.getJudge().getDockerImage());
        command.add("sh");
        command.add("-c");
        command.add("g++ /code/" + JudgeWorkspace.SOURCE_FILE
            + " -o /code/" + JudgeWorkspace.BINARY_FILE
            + " && chmod +x /code/" + JudgeWorkspace.BINARY_FILE);

        long started = System.nanoTime();
        JudgeProcessResult processResult = dockerProcessService.run(
            command,
            Duration.ofMillis(appProperties.getJudge().getCompileTimeoutMs()),
            containerName
        );
        long durationMs = elapsedMillis(started);
        log.info(
            "Compilation finished submissionId={} exitCode={} timedOut={} durationMs={}",
            workspace.submissionId(),
            processResult.exitCode(),
            processResult.timedOut(),
            durationMs
        );
        return new CompileResult(processResult, durationMs);
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String safeContainerSuffix(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
