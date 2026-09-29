package com.divzero.security;

import com.divzero.entity.Role;

public record AuthenticatedUser(String id, String name, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
