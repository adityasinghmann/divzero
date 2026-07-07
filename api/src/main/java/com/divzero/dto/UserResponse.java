package com.divzero.dto;

import com.divzero.entity.UserEntity;

public record UserResponse(String id, String name, String username, String password, String role) {

    public static UserResponse fromEntity(UserEntity user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getUsername(),
            user.getPassword(),
            user.getRole().name()
        );
    }
}
