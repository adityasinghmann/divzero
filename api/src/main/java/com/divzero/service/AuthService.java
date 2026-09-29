package com.divzero.service;

import com.divzero.dto.SigninRequest;
import com.divzero.dto.SignupRequest;
import com.divzero.security.AuthenticatedUser;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

public interface AuthService {

    ResponseEntity<Object> signup(SignupRequest request);

    ResponseEntity<Object> signin(SigninRequest request);

    Optional<AuthenticatedUser> authenticate(String authorizationHeader);
}
