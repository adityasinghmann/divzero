package com.divzero.config;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Derives a per-instance Kafka consumer group id for the submission-completion listener.
 *
 * <p>Completion events must reach <em>every</em> API instance, not one of them. The synchronous
 * submit path parks the caller on an in-process {@code CompletableFuture}, so if instance A holds
 * the waiting future and a shared consumer group hands the completion event to instance B, A's
 * future never completes and the request blocks until it times out. Giving each instance its own
 * group id turns the completion topic into a broadcast: every instance sees every event, releases
 * any future it happens to be holding, and ignores the rest.
 *
 * <p>Implemented as an {@link EnvironmentPostProcessor} rather than the alternatives because
 * {@code @KafkaListener(groupId = "...")} resolves a property placeholder, not a bean reference, so
 * a {@code @Bean String} is not visible to it. Setting a system property in
 * {@code DivZeroApplication.main} would work for a deployed process but leaks a JVM-wide global,
 * which is wrong when several application contexts share one JVM (as they do under test). An
 * environment post-processor scopes the generated value to a single {@code Environment} and still
 * runs early enough for listener registration to see it.
 *
 * <p>Ordered last so that config data (application.yml, environment overrides) has already been
 * contributed and the configured base group id can be read from it.
 */
public class CompletionGroupIdEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String INSTANCE_GROUP_ID_PROPERTY = "app.kafka.completion-group-id-instance";

    private static final String BASE_GROUP_ID_PROPERTY = "app.kafka.completion-group-id";
    private static final String DEFAULT_BASE_GROUP_ID = "divzero-submission-completion";
    private static final String PROPERTY_SOURCE_NAME = "divzeroCompletionGroupId";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        String base = environment.getProperty(BASE_GROUP_ID_PROPERTY, DEFAULT_BASE_GROUP_ID);
        String instanceGroupId = base + "-" + UUID.randomUUID();
        environment.getPropertySources().addFirst(new MapPropertySource(
            PROPERTY_SOURCE_NAME,
            Map.of(INSTANCE_GROUP_ID_PROPERTY, instanceGroupId)
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
