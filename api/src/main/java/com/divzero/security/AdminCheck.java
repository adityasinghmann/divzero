package com.divzero.security;

import org.springframework.security.core.Authentication;

/** Reads the role that JwtAuthenticationFilter granted, for endpoints whose output depends on it. */
public final class AdminCheck {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private AdminCheck() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
