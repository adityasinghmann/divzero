package com.divzero.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitCodeRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String userId;

    @NotBlank
    private String problemId;
}
