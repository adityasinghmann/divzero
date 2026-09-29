package com.divzero.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.divzero.entity.SubmissionEntity;
import com.divzero.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmissionResponseTest {

    // Configured like Spring Boot's auto-configured mapper: ISO strings, not epoch numbers.
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("submission time is serialized with an explicit UTC offset")
    void timeCarriesUtcOffset() throws Exception {
        LocalDateTime created = LocalDateTime.of(2026, 9, 28, 22, 47, 44);
        SubmissionEntity entity = SubmissionEntity.builder().id("s1").verdict("Accepted").time(created).build();

        SubmissionResponse response = SubmissionResponse.fromEntity(entity);
        String json = objectMapper.writeValueAsString(response);

        Instant expected = created.atZone(ZoneId.systemDefault()).toInstant();
        assertThat(response.time()).isEqualTo(expected);
        assertThat(json).contains("\"time\":\"" + expected + "\"");
        assertThat(expected.toString()).endsWith("Z");
    }

    @Test
    @DisplayName("generated CUIDs never contain a sign character")
    void cuidHasNoSign() {
        for (int i = 0; i < 10_000; i++) {
            assertThat(IdGenerator.cuid()).startsWith("c").doesNotContain("-");
        }
    }
}
