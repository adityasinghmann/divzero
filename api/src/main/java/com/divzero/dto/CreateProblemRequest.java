package com.divzero.dto;

import com.divzero.entity.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProblemRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 50000)
    private String description;

    /** Optional; the column default (2000 ms) applies when omitted. */
    @Min(100)
    @Max(15000)
    private Long timeLimitMs;

    /** Optional; the column default (256 MB) applies when omitted. */
    @Min(16)
    @Max(1024)
    private Integer memoryLimitMb;

    /** Optional; defaults to MEDIUM when omitted. */
    private Difficulty difficulty;
}
