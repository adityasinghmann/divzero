package com.divzero.entity;

import com.divzero.util.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "`Test`")
public class TestCaseEntity {

    @Id
    @Column(name = "`id`", nullable = false)
    private String id;

    @Column(name = "`problemId`", nullable = false)
    private String problemId;

    @Column(name = "`input`", nullable = false)
    private String input;

    @Column(name = "`correctOutput`", nullable = false)
    private String correctOutput;

    /** Sample tests are visible to every user; all others are hidden from non-admins. */
    @Column(name = "`isSample`", nullable = false)
    private boolean sample;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`problemId`", referencedColumnName = "`id`", insertable = false, updatable = false)
    private ProblemEntity problem;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.cuid();
        }
    }
}
