package com.divzero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {

    /** Shown on the leaderboard and used to sign in, so it is kept to a plain, bounded charset. */
    @NotBlank
    @Size(min = 3, max = 32)
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "may only contain letters, digits, '_', '.' and '-'")
    private String username;

    @NotBlank
    @Size(max = 64)
    private String name;

    /**
     * BCrypt only uses the first 72 bytes of a password and silently ignores the rest, so a
     * longer one would give a false sense of strength. 72 characters is a conservative bound.
     */
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
}
