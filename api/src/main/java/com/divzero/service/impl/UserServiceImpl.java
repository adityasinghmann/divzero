package com.divzero.service.impl;

import com.divzero.dto.UserResponse;
import com.divzero.repository.UserRepository;
import com.divzero.service.AuthService;
import com.divzero.service.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getUsers(String authorizationHeader) {
        if (!authService.isAdmin(authorizationHeader)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "User not identified"));
        }

        try {
            List<UserResponse> users = userRepository.findAll()
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
            return ResponseEntity.ok(users);
        } catch (Exception error) {
            log.warn("Error finding users", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error finding users"));
        }
    }
}
