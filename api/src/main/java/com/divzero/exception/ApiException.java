package com.divzero.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> body;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.body = Map.of("message", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
