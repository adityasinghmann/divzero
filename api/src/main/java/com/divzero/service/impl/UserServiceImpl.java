package com.divzero.service.impl;

import com.divzero.dto.UserResponse;
import com.divzero.repository.UserRepository;
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

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getUsers(String authorizationHeader) {
        // Admin authorization is enforced by @PreAuthorize on UserController.getUsers.
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

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getCurrentUser(String userId) {
        // The filter already resolved this id from the database, so a miss means the user was
        // deleted between the filter and here; treat it like any other stale token.
        return userRepository.findById(userId)
            .<ResponseEntity<Object>>map(user -> ResponseEntity.ok(UserResponse.fromEntity(user)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Authentication required")));
    }
}
