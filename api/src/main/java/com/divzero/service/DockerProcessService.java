package com.divzero.service;

import com.divzero.dto.JudgeProcessResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface DockerProcessService {

    JudgeProcessResult run(List<String> command, Duration timeout, String containerName)
        throws IOException, InterruptedException;

    List<String> dockerRunBase(Path workDir, String containerName);
}
