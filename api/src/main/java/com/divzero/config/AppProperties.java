package com.divzero.config;

import jakarta.annotation.PostConstruct;
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

    /** HMAC-256 uses a 32-byte digest; a shorter key weakens the signature. */
    private static final int MIN_JWT_SECRET_LENGTH = 32;

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

    @Valid
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        /** No default: an unset JWT_SECRET must fail startup rather than silently sign with a known key. */
        @NotBlank(message = "must be supplied via the JWT_SECRET environment variable")
        private String secret;

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

        /** Caps the process table inside the sandbox so a fork bomb cannot exhaust host PIDs. */
        @Min(1)
        private int pidsLimit = 64;

        /** Mounts the container root read-only; /code (a bind mount) and /tmp stay writable. */
        private boolean readOnlyRootfs = true;

        /** Runs the sandbox as an unprivileged uid:gid ("nobody" on Alpine). */
        @NotBlank
        private String runAsUser = "65534:65534";

        /** Size of the writable /tmp tmpfs supplied when readOnlyRootfs is enabled. */
        @Min(1)
        private long tmpfsSizeBytes = 67108864L;

        /**
         * Whether this process consumes submissions and runs them in Docker. Disable it on the
         * public API so only a separate, non-internet-facing judge process needs the Docker socket.
         */
        private boolean workerEnabled = true;
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

        /** Short on purpose: past this the caller gets 202 and falls back to polling. */
        @Min(1)
        private long submitWaitTimeoutMs = 5000L;

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

    /**
     * Bean-validation catches a blank secret; this catches a present-but-weak one.
     * HMAC-256 keys shorter than the 32-byte digest size reduce the effective
     * strength of the signature, so we refuse to boot with one.
     */
    @PostConstruct
    void validateJwtSecret() {
        String secret = jwt == null ? null : jwt.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "app.jwt.secret is not set. Set the JWT_SECRET environment variable to a random "
                    + "value of at least " + MIN_JWT_SECRET_LENGTH + " characters."
            );
        }
        if (secret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException(
                "app.jwt.secret is too short (" + secret.length() + " characters). Set the JWT_SECRET "
                    + "environment variable to a random value of at least " + MIN_JWT_SECRET_LENGTH
                    + " characters."
            );
        }
    }

    @Data
    public static class RateLimit {
        @Min(1)
        private int submissionsPerWindow = 10;

        @Min(1)
        private long windowSeconds = 60L;
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
