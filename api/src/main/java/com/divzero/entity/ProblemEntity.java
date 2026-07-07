package com.divzero.entity;

import com.divzero.util.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Id
    @Column(name = "`id`", nullable = false)
    private String id;

    @Column(name = "`title`", nullable = false)
    private String title;

    @Column(name = "`description`", nullable = false)
    private String description;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.cuid();
        }
    }
}
