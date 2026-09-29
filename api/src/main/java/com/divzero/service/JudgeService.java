package com.divzero.service;

import com.divzero.dto.JudgeResult;
import com.divzero.dto.JudgeWorkspace;
import com.divzero.event.SubmissionCreatedEvent;
import java.io.IOException;
import java.nio.file.Path;

public interface JudgeService {

    JudgeResult judge(SubmissionCreatedEvent event);

    JudgeWorkspace createWorkspace(String submissionId) throws IOException;

    void cleanupWorkspace(Path workDir);
}
