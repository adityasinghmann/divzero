package com.divzero.service.impl;

import com.divzero.config.AppProperties;
import com.divzero.dto.JudgeProcessResult;
import com.divzero.service.DockerProcessService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerProcessServiceImpl implements DockerProcessService {

    private final AppProperties appProperties;

    @Override
    public JudgeProcessResult run(List<String> command, Duration timeout, String containerName)
        throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
            () -> readStream(process.getInputStream(), appProperties.getJudge().getOutputLimitBytes())
        );
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
            () -> readStream(process.getErrorStream(), appProperties.getJudge().getOutputLimitBytes())
        );

        boolean finished = false;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("Docker process timed out, container={}", containerName);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new JudgeProcessResult(124, futureValue(stdout), futureValue(stderr), true);
            }

            return new JudgeProcessResult(
                process.exitValue(),
                futureValue(stdout),
                futureValue(stderr),
                false
            );
        } finally {
            if (!finished) {
                process.destroyForcibly();
            }
            removeContainer(containerName);
        }
    }

    @Override
    public List<String> dockerRunBase(Path workDir, String containerName, String memoryLimit) {
        List<String> command = dockerCommand();
        command.add("run");
        command.add("--rm");
        // Without an init process the shell execs `timeout` into PID 1, where the SIGTERM it
        // sends to the submitted program has no effect and the in-container time limit silently
        // never fires. tini also reaps the orphans a forking submission leaves behind.
        command.add("--init");
        command.add("--name");
        command.add(containerName);
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(memoryLimit);
        // Without this Docker also grants the same amount again as swap, so a program could use
        // up to twice the problem's declared limit before being stopped.
        command.add("--memory-swap");
        command.add(memoryLimit);
        command.add("--cpus");
        command.add(appProperties.getJudge().getCpus());
        command.add("--pids-limit");
        command.add(String.valueOf(appProperties.getJudge().getPidsLimit()));
        command.add("--security-opt");
        command.add("no-new-privileges");
        command.add("--user");
        command.add(appProperties.getJudge().getRunAsUser());
        if (appProperties.getJudge().isReadOnlyRootfs()) {
            // --read-only does not apply to bind mounts, so /code stays writable for the
            // compiler output; /tmp is supplied separately as a small non-executable tmpfs.
            command.add("--read-only");
            command.add("--tmpfs");
            command.add("/tmp:rw,noexec,nosuid,size="
                + appProperties.getJudge().getTmpfsSizeBytes());
        }
        command.add("-v");
        command.add(volumeMount(workDir));
        return command;
    }

    private String readStream(InputStream stream, int maxBytes) {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                int remaining = maxBytes - total;
                if (remaining > 0) {
                    int writeLength = Math.min(read, remaining);
                    output.write(buffer, 0, writeLength);
                    total += writeLength;
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            log.warn("Failed to read process stream", error);
            return "";
        }
    }

    private String futureValue(CompletableFuture<String> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException error) {
            return "";
        }
    }

    private void removeContainer(String containerName) {
        try {
            List<String> command = dockerCommand();
            command.add("rm");
            command.add("-f");
            command.add(containerName);
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            log.debug("Container cleanup did not find active container {}", containerName);
        }
    }

    private List<String> dockerCommand() {
        return new ArrayList<>(
            List.of(appProperties.getJudge().getDockerCommand().trim().split("\\s+"))
        );
    }

    private String volumeMount(Path workDir) {
        String label = appProperties.getJudge().getVolumeLabel();
        String mount = workDir.toAbsolutePath().normalize() + ":/code";
        if (label == null || label.isBlank()) {
            return mount;
        }
        return mount + ":" + label;
    }
}
