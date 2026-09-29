package com.divzero.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.divzero.entity.UserEntity;
import java.util.Optional;

public interface JwtService {

    String generateToken(UserEntity user);

    Optional<DecodedJWT> verify(String token);

    String extractToken(String authorizationHeader);
}
