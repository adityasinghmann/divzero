package com.divzero.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
        MethodArgumentNotValidException error,
        HttpServletRequest request
    ) {
        return validationErrorForPath(request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableJson(
        HttpMessageNotReadableException error,
        HttpServletRequest request
    ) {
        return validationErrorForPath(request.getRequestURI());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException error) {
        return ResponseEntity.status(error.getStatus()).body(error.getBody());
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception error, HttpServletRequest request) {
        log.debug("Bad API request path={}", request.getRequestURI(), error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("Invalid request", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
        DataIntegrityViolationException error,
        HttpServletRequest request
    ) {
        log.warn("Data integrity violation path={}", request.getRequestURI(), error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("Invalid data", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
        NoResourceFoundException error,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody("Not Found", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception error, HttpServletRequest request) {
        log.error("Unhandled API error path={}", request.getRequestURI(), error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("Internal server error", request.getRequestURI()));
    }

    private ResponseEntity<Map<String, String>> validationErrorForPath(String path) {
        if (path.endsWith("/signup")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request body"));
        }
        if (path.endsWith("/signin")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Invalid request body"));
        }
        if (path.endsWith("/create-problem") || path.endsWith("/create-test")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required fields"));
        }
        if (path.endsWith("/submit-code")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required parameters"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "Invalid request body"));
    }

    private Map<String, Object> errorBody(String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("message", message);
        body.put("path", path);
        return body;
    }
}
