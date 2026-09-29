package com.divzero.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.divzero.config.AppProperties;
import com.divzero.entity.UserEntity;
import com.divzero.service.JwtService;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final AppProperties appProperties;

    @Override
    public String generateToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(appProperties.getJwt().getExpirationMs());
        return JWT.create()
            .withSubject(user.getId())
            .withClaim("username", user.getUsername())
            .withClaim("id", user.getId())
            .withClaim("name", user.getName())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm());
    }

    @Override
    public Optional<DecodedJWT> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTVerifier verifier = JWT.require(algorithm()).build();
            return Optional.of(verifier.verify(token));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(appProperties.getJwt().getSecret());
    }
}
