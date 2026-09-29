package com.divzero.dto;

import java.nio.file.Path;

public record JudgeWorkspace(String submissionId, Path directory) {

    public static final String SOURCE_FILE = "main.cpp";
    public static final String BINARY_FILE = "main";

    public Path sourcePath() {
        return directory.resolve(SOURCE_FILE);
    }
}
