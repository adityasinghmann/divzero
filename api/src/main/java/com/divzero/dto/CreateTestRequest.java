package com.divzero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTestRequest {

    @NotBlank
    private String problemId;

    /** May be empty: a problem that reads no stdin still needs tests. */
    @NotNull
    private String input;

    @NotBlank
    private String correctOutput;

    /** Optional; a test is hidden unless marked as a sample. */
    private Boolean sample;
}
