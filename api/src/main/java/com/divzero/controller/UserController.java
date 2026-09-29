package com.divzero.controller;

import com.divzero.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/view-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> getUsers(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return userService.getUsers(authorizationHeader);
    }

    /** The caller's own profile and role; the principal name is the user id set by JwtAuthenticationFilter. */
    @GetMapping("/me")
    public ResponseEntity<Object> getCurrentUser(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
    }
}
