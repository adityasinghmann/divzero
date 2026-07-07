package com.divzero.dto;

import com.divzero.entity.ProblemEntity;

public record ProblemResponse(String id, String title, String description) {

    public static ProblemResponse fromEntity(ProblemEntity problem) {
        return new ProblemResponse(problem.getId(), problem.getTitle(), problem.getDescription());
    }
}
