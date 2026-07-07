package com.divzero.entity;

import com.divzero.util.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "`Submission`")
public class SubmissionEntity {

    @Id
    @Column(name = "`id`", nullable = false)
    private String id;

    @CreationTimestamp
    @Column(name = "`time`", nullable = false, updatable = false)
    private LocalDateTime time;

    @Column(name = "`userId`", nullable = false)
    private String userId;

    @Column(name = "`problemId`", nullable = false)
    private String problemId;

    @Column(name = "`verdict`", nullable = false)
    private String verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "`status`", nullable = false, columnDefinition = "TEXT")
    private SubmissionStatus status;

    @Column(name = "`compileTimeMs`")
    private Long compileTimeMs;

    @Column(name = "`executionTimeMs`")
    private Long executionTimeMs;

    @Column(name = "`memoryUsageKb`")
    private Long memoryUsageKb;

    @Column(name = "`language`", nullable = false)
    private String language;

    @Column(name = "`judgeLogs`", columnDefinition = "TEXT")
    private String judgeLogs;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.cuid();
        }
        if (status == null) {
            status = SubmissionStatus.QUEUED;
        }
        if (verdict == null || verdict.isBlank()) {
            verdict = status.displayVerdict();
        }
        if (language == null || language.isBlank()) {
            language = "cpp";
        }
    }
}
