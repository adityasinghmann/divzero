package com.divzero.service;

import com.divzero.dto.CompileResult;
import com.divzero.dto.JudgeWorkspace;
import java.io.IOException;

public interface CompilerService {

    CompileResult compile(JudgeWorkspace workspace) throws IOException, InterruptedException;
}
