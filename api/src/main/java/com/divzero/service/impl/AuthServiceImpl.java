package com.divzero.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.divzero.dto.SigninRequest;
import com.divzero.dto.SignupRequest;
import com.divzero.entity.Role;
import com.divzero.entity.UserEntity;
import com.divzero.exception.ApiException;
import com.divzero.repository.UserRepository;
import com.divzero.security.AuthenticatedUser;
import com.divzero.service.AuthService;
import com.divzero.service.JwtService;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String USERNAME_TAKEN_MESSAGE = "Username already taken";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // A benign race: two threads may both encode it, and either result is valid.
    private volatile String dummyHash;

    @Override
    @Transactional
    public ResponseEntity<Object> signup(SignupRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, USERNAME_TAKEN_MESSAGE);
        }

        UserEntity user = UserEntity.builder()
            .name(request.getName())
            .username(request.getUsername())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(Role.USER)
            .build();

        try {
            // saveAndFlush, not save: the unique-username violation has to surface inside this
            // try block. A deferred flush would raise it after the method returns, where the
            // race guard below could not turn it into a 409.
            UserEntity saved = userRepository.saveAndFlush(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ok", "token", jwtService.generateToken(saved)));
        } catch (DataIntegrityViolationException error) {
            // Two concurrent signups for the same username: the check above passed for both.
            log.info("Duplicate username rejected at insert username={}", request.getUsername());
            throw new ApiException(HttpStatus.CONFLICT, USERNAME_TAKEN_MESSAGE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> signin(SigninRequest request) {
        Optional<UserEntity> user = userRepository.findByUsername(request.getUsername());

        // An unknown username and a wrong password deliberately produce the same 401 with the
        // same wording: telling them apart would let anyone enumerate registered usernames.
        // They must also take the same time. BCrypt dominates the cost of this method, so an
        // unknown username still pays for one comparison, against a fixed dummy hash.
        String storedHash = user.map(UserEntity::getPassword).orElse(dummyHash());
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), storedHash);
        if (user.isEmpty() || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        return ResponseEntity.ok(
            Map.of("status", "success", "token", jwtService.generateToken(user.get()))
        );
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

    /** Encoded once, lazily, with the real encoder so its cost factor matches stored hashes. */
    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode("divzero-timing-equaliser");
            dummyHash = hash;
        }
        return hash;
    }

    private String userIdFromToken(DecodedJWT decodedJWT) {
        String subject = decodedJWT.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return decodedJWT.getClaim("id").asString();
    }
}
