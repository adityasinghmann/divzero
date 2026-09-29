package com.divzero.service;

import com.divzero.dto.JudgeProcessResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface DockerProcessService {

    JudgeProcessResult run(List<String> command, Duration timeout, String containerName)
        throws IOException, InterruptedException;

    /**
     * @param memoryLimit Docker-format memory limit (for example "256m"). Supplied by the
     *                    caller rather than read from configuration so that execution can use
     *                    the per-problem limit while compilation keeps the global one.
     */
    List<String> dockerRunBase(Path workDir, String containerName, String memoryLimit);
}
