package com.divzero.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String frontendUrl = "http://localhost:5173";

    @Valid
    private Jwt jwt = new Jwt();

    @Valid
    private Judge judge = new Judge();

    @Valid
    private Kafka kafka = new Kafka();

    @Valid
    private Cache cache = new Cache();

    @Data
    public static class Jwt {
        @NotBlank
        private String secret = "test";

        @Min(1)
        private long expirationMs = 604800000L;
    }

    @Data
    public static class Judge {
        @NotBlank
        private String dockerCommand = "docker";

        @NotBlank
        private String dockerImage = "frolvlad/alpine-gxx";

        private String workspaceRoot = "";

        @NotBlank
        private String memory = "256m";

        @NotBlank
        private String cpus = "0.5";

        @Min(1)
        private long executionTimeoutMs = 10000L;

        @Min(1)
        private long compileTimeoutMs = 120000L;

        @Min(1)
        private int outputLimitBytes = 1048576;

        private String volumeLabel = "";
    }

    @Data
    public static class Kafka {
        @Valid
        private Topics topics = new Topics();

        @NotBlank
        private String judgeGroupId = "divzero-judge-workers";

        @NotBlank
        private String completionGroupId = "divzero-submission-completion";

        @NotBlank
        private String deadLetterGroupId = "divzero-dead-letter-handler";

        @Min(1)
        private int retryMaxAttempts = 3;

        @Min(0)
        private long retryBackoffMs = 2000L;

        @Min(1)
        private long publishTimeoutMs = 10000L;

        @Min(1)
        private long submitWaitTimeoutMs = 180000L;

        @Min(1)
        private int topicPartitions = 3;

        @Min(1)
        private short topicReplicas = 1;
    }

    @Data
    public static class Topics {
        @NotBlank
        private String submissionCreated = "divzero.submission.created";

        @NotBlank
        private String submissionCompleted = "divzero.submission.completed";

        @NotBlank
        private String submissionCreatedDlt = "divzero.submission.created.DLT";

        @NotBlank
        private String submissionCompletedDlt = "divzero.submission.completed.DLT";
    }

    @Data
    public static class Cache {
        @Min(1)
        private long problemListTtlSeconds = 300L;

        @Min(1)
        private long problemDetailsTtlSeconds = 300L;

        @Min(1)
        private long leaderboardTtlSeconds = 120L;
    }
}
