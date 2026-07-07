package com.divzero.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTestRequest {

    @NotBlank
    private String problemId;

    @NotBlank
    private String input;

    @NotBlank
    private String correctOutput;
}
