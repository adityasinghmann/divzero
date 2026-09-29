package com.divzero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitCodeRequest {

    /**
     * 64 KB, the usual judge limit. The source travels inside a Kafka message, and anything near
     * the broker's 1 MB default message size would fail to publish and surface as a 500.
     */
    @NotBlank
    @Size(max = 65536)
    private String code;

    @NotBlank
    private String problemId;
}
