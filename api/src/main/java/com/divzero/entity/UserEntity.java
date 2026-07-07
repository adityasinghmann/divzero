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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "`User`")
public class UserEntity {

    @Id
    @Column(name = "`id`", nullable = false)
    private String id;

    @Column(name = "`name`", nullable = false)
    private String name;

    @Column(name = "`username`", nullable = false, unique = true)
    private String username;

    @Column(name = "`password`", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "`role`", nullable = false, columnDefinition = "\"Roles\"")
    private Role role;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.uuid();
        }
        if (role == null) {
            role = Role.USER;
        }
    }
}
