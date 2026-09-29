package com.divzero.dto;

import com.divzero.entity.TestCaseEntity;

public record TestResponse(String id, String problemId, String input, String correctOutput, boolean sample) {

    public static TestResponse fromEntity(TestCaseEntity test) {
        return new TestResponse(
            test.getId(),
            test.getProblemId(),
            test.getInput(),
            test.getCorrectOutput(),
            test.isSample()
        );
    }
}
