package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.CompileResult;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.entity.ProblemEntity;
import com.divzero.entity.TestCaseEntity;
import com.divzero.event.SubmissionCreatedEvent;
import com.divzero.repository.ProblemRepository;
import com.divzero.repository.TestCaseRepository;
import com.divzero.service.CompilerService;
import com.divzero.service.ExecutionService;
import com.divzero.service.JudgeService;
import com.divzero.service.VerdictService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    private final AppProperties appProperties;
    private final TestCaseRepository testCaseRepository;
    private final ProblemRepository problemRepository;
    private final CompilerService compilerService;
    private final ExecutionService executionService;
    private final VerdictService verdictService;

    @Override
    public JudgeResult judge(SubmissionCreatedEvent event) {
        JudgeWorkspace workspace = null;
        CompileResult compileResult = null;
        List<ExecutionResult> executions = new ArrayList<>();

        try {
            workspace = createWorkspace(event.submissionId());
            Files.writeString(workspace.sourcePath(), event.code(), StandardCharsets.UTF_8);

            List<TestCaseEntity> tests = testCaseRepository.findByProblemIdOrderByIdAsc(event.problemId());
            // Never judge against an empty suite: the loop below would run nothing and fall through
            // to Accepted. Submit rejects this up front; this guards events already in flight.
            if (tests.isEmpty()) {
                throw new IllegalStateException("Problem " + event.problemId() + " has no test cases");
            }

            // SubmissionCreatedEvent does not carry the limits, so they are read from the problem
            // row here. The configured app.judge values are only a fallback for a problem row that
            // is missing or has no value.
            ProblemEntity problem = problemRepository.findById(event.problemId()).orElse(null);
            long timeLimitMs = resolveTimeLimitMs(problem);
            String memoryLimit = resolveMemoryLimit(problem);

            log.info(
                "Starting judge submissionId={} userId={} problemId={} tests={} timeLimitMs={} memory={}",
                event.submissionId(),
                event.userId(),
                event.problemId(),
                tests.size(),
                timeLimitMs,
                memoryLimit
            );

            compileResult = compilerService.compile(workspace);
            if (!compileResult.successful()) {
                return verdictService.compilationError(compileResult);
            }

            for (int i = 0; i < tests.size(); i++) {
                ExecutionResult execution = executionService.execute(
                    workspace,
                    tests.get(i),
                    i,
                    timeLimitMs,
                    memoryLimit
                );
                executions.add(execution);

                Optional<JudgeResult> verdict = verdictService.evaluateExecution(compileResult, executions);
                if (verdict.isPresent()) {
                    return verdict.get();
                }
            }

            return verdictService.accepted(compileResult, executions);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return verdictService.internalError(compileResult, executions, error);
        } finally {
            cleanupWorkspace(workspace == null ? null : workspace.directory());
        }
    }

    private long resolveTimeLimitMs(ProblemEntity problem) {
        if (problem != null && problem.getTimeLimitMs() != null) {
            return problem.getTimeLimitMs();
        }
        return appProperties.getJudge().getExecutionTimeoutMs();
    }

    private String resolveMemoryLimit(ProblemEntity problem) {
        if (problem != null && problem.getMemoryLimitMb() != null) {
            return problem.getMemoryLimitMb() + "m";
        }
        return appProperties.getJudge().getMemory();
    }

    @Override
    public JudgeWorkspace createWorkspace(String submissionId) throws IOException {
        String workspaceRoot = appProperties.getJudge().getWorkspaceRoot();
        Path directory;
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            directory = Files.createTempDirectory("divzero_" + submissionId + "_");
        } else {
            Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            directory = Files.createTempDirectory(root, "divzero_" + submissionId + "_");
        }
        makeWorkspaceWritableBySandboxUser(directory);
        return new JudgeWorkspace(submissionId, directory);
    }

    /**
     * The sandbox runs as an unprivileged uid (app.judge.run-as-user), which will not match the
     * uid that owns this freshly created directory, so the compiler could not write its binary
     * into the bind mount. Widening the directory to rwxrwxrwx is safe because it lives under a
     * private per-submission temp directory that is deleted after judging.
     * Skipped on filesystems without POSIX attribute support (Windows), where the Docker Desktop
     * bind-mount layer already presents the mount as world-writable.
     */
    private void makeWorkspaceWritableBySandboxUser(Path directory) {
        try {
            if (!Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class)) {
                return;
            }
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (IOException | UnsupportedOperationException error) {
            log.warn("Could not relax permissions on judge workspace {}", directory, error);
        }
    }

    @Override
    public void cleanupWorkspace(Path workDir) {
        if (workDir == null) {
            return;
        }
        try (var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    log.warn("Failed to delete temporary path {}", path, error);
                }
            });
        } catch (IOException error) {
            log.warn("Failed to cleanup temporary judge directory {}", workDir, error);
        }
    }
}
