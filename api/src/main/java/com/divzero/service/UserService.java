package com.divzero.service;

import org.springframework.http.ResponseEntity;

public interface UserService {

    ResponseEntity<Object> getUsers(String authorizationHeader);

    ResponseEntity<Object> getCurrentUser(String userId);
}
