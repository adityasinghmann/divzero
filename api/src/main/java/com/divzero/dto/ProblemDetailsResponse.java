package com.divzero.dto;

import java.util.List;

public record ProblemDetailsResponse(
    String id,
    String title,
    String description,
    List<TestResponse> tests
) {
}
