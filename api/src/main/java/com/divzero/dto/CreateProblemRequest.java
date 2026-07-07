package com.divzero.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProblemRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;
}
