package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.CompileResult;
import com.divzero.dto.ExecutionResult;
import com.divzero.dto.JudgeResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.entity.TestCaseEntity;
import com.divzero.event.SubmissionCreatedEvent;
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

            List<TestCaseEntity> tests = testCaseRepository.findByProblemId(event.problemId());
            log.info(
                "Starting judge submissionId={} userId={} problemId={} tests={}",
                event.submissionId(),
                event.userId(),
                event.problemId(),
                tests.size()
            );

            compileResult = compilerService.compile(workspace);
            if (!compileResult.successful()) {
                return verdictService.compilationError(compileResult);
            }

            for (int i = 0; i < tests.size(); i++) {
                ExecutionResult execution = executionService.execute(workspace, tests.get(i), i);
                executions.add(execution);

                Optional<JudgeResult> verdict = verdictService.evaluateExecution(compileResult, executions);
                if (verdict.isPresent()) {
                    return verdict.get();
                }
            }

            return verdictService.accepted(compileResult, executions);
        } catch (Exception error) {
            return verdictService.internalError(compileResult, executions, error);
        } finally {
            cleanupWorkspace(workspace == null ? null : workspace.directory());
        }
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
        return new JudgeWorkspace(submissionId, directory);
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
