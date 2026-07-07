package com.divzero.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.divzero.dto.SigninRequest;
import com.divzero.dto.SignupRequest;
import com.divzero.entity.Role;
import com.divzero.entity.UserEntity;
import com.divzero.repository.UserRepository;
import com.divzero.security.AuthenticatedUser;
import com.divzero.service.AuthService;
import com.divzero.service.JwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public ResponseEntity<Object> signup(SignupRequest request) {
        try {
            UserEntity user = UserEntity.builder()
                .name(request.getName())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

            UserEntity saved = userRepository.save(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ok", "token", jwtService.generateToken(saved)));
        } catch (Exception error) {
            log.warn("Error while creating the user", error);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Error while creating the user");
            body.put("error", error.toString());
            return ResponseEntity.ok(body);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> signin(SigninRequest request) {
        try {
            Optional<UserEntity> user = userRepository.findByUsername(request.getUsername());
            if (user.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User does not exists"));
            }

            if (passwordEncoder.matches(request.getPassword(), user.get().getPassword())) {
                return ResponseEntity.ok(
                    Map.of("status", "success", "token", jwtService.generateToken(user.get()))
                );
            }

            return ResponseEntity.ok(Map.of("message", "Invalid password!"));
        } catch (Exception error) {
            log.warn("There was an error signing in", error);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "There was an error signing in!");
            body.put("error", error.toString());
            return ResponseEntity.ok(body);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String authorizationHeader) {
        String token = jwtService.extractToken(authorizationHeader);
        return jwtService.verify(token)
            .map(this::userIdFromToken)
            .flatMap(userRepository::findById)
            .map(user -> new AuthenticatedUser(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getRole()
            ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAdmin(String authorizationHeader) {
        return authenticate(authorizationHeader)
            .map(AuthenticatedUser::isAdmin)
            .orElse(false);
    }

    private String userIdFromToken(DecodedJWT decodedJWT) {
        String subject = decodedJWT.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return decodedJWT.getClaim("id").asString();
    }
}
