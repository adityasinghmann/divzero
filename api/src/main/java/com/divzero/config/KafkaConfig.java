package com.divzero.config;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final AppProperties appProperties;
    private final KafkaProperties kafkaProperties;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        properties.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.divzero.event,java.util,java.lang");
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory,
        CommonErrorHandler commonErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(commonErrorHandler);
        return factory;
    }

    @Bean
    public CommonErrorHandler commonErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(deadLetterTopic(record.topic()), record.partition())
        );
        long retries = Math.max(0, appProperties.getKafka().getRetryMaxAttempts() - 1L);
        return new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(appProperties.getKafka().getRetryBackoffMs(), retries)
        );
    }

    @Bean
    public NewTopic submissionCreatedTopic() {
        return topic(appProperties.getKafka().getTopics().getSubmissionCreated());
    }

    @Bean
    public NewTopic submissionCompletedTopic() {
        return topic(appProperties.getKafka().getTopics().getSubmissionCompleted());
    }

    @Bean
    public NewTopic submissionCreatedDeadLetterTopic() {
        return topic(appProperties.getKafka().getTopics().getSubmissionCreatedDlt());
    }

    @Bean
    public NewTopic submissionCompletedDeadLetterTopic() {
        return topic(appProperties.getKafka().getTopics().getSubmissionCompletedDlt());
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
            .partitions(appProperties.getKafka().getTopicPartitions())
            .replicas(appProperties.getKafka().getTopicReplicas())
            .build();
    }

    private String deadLetterTopic(String sourceTopic) {
        AppProperties.Topics topics = appProperties.getKafka().getTopics();
        if (topics.getSubmissionCreated().equals(sourceTopic)) {
            return topics.getSubmissionCreatedDlt();
        }
        if (topics.getSubmissionCompleted().equals(sourceTopic)) {
            return topics.getSubmissionCompletedDlt();
        }
        return sourceTopic + ".DLT";
    }
}
