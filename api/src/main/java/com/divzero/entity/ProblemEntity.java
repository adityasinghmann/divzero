package com.divzero.entity;

import com.divzero.util.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "`Problem`")
public class ProblemEntity {

    public static final long DEFAULT_TIME_LIMIT_MS = 2000L;
    public static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    @Id
    @Column(name = "`id`", nullable = false)
    private String id;

    @Column(name = "`title`", nullable = false)
    private String title;

    @Column(name = "`description`", nullable = false)
    private String description;

    @Column(name = "`timeLimitMs`", nullable = false)
    private Long timeLimitMs;

    @Column(name = "`memoryLimitMb`", nullable = false)
    private Integer memoryLimitMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "`difficulty`", nullable = false, columnDefinition = "TEXT")
    private Difficulty difficulty;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.cuid();
        }
        // Mirrors the column defaults in V4 so an omitted limit does not fail the NOT NULL insert.
        if (timeLimitMs == null) {
            timeLimitMs = DEFAULT_TIME_LIMIT_MS;
        }
        if (memoryLimitMb == null) {
            memoryLimitMb = DEFAULT_MEMORY_LIMIT_MB;
        }
        if (difficulty == null) {
            difficulty = Difficulty.MEDIUM;
        }
    }
}
