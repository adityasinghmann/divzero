package com.divzero.dto;

import com.divzero.entity.UserEntity;

/** Never carries the password hash: this shape is serialized straight to the browser. */
public record UserResponse(String id, String name, String username, String role) {

    public static UserResponse fromEntity(UserEntity user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getUsername(),
            user.getRole().name()
        );
    }
}
