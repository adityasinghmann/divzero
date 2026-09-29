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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean-validation failures report which field failed and why, rather than a single opaque
     * "invalid request" string, so a caller can correct the request without guessing.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
        MethodArgumentNotValidException error,
        HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : error.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(
                fieldError.getField(),
                fieldError.getDefaultMessage() == null ? "is invalid" : fieldError.getDefaultMessage()
            );
        }
        for (ObjectError globalError : error.getBindingResult().getGlobalErrors()) {
            fieldErrors.putIfAbsent(
                globalError.getObjectName(),
                globalError.getDefaultMessage() == null ? "is invalid" : globalError.getDefaultMessage()
            );
        }

        log.debug("Validation failed path={} fields={}", request.getRequestURI(), fieldErrors.keySet());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Validation failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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

    /**
     * Method-security denials (@PreAuthorize) are thrown inside the controller, so this advice
     * sees them before Spring Security's ExceptionTranslationFilter can invoke
     * JsonAccessDeniedHandler. Without this handler the catch-all below would turn a 403 into
     * a 500. The body matches JsonAccessDeniedHandler so both paths look the same to clients.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
        AccessDeniedException error,
        HttpServletRequest request
    ) {
        log.debug("Access denied path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", "Access denied"));
    }

    /**
     * Client mistakes in how a route is called (wrong verb, missing JSON content type, an
     * unsatisfiable Accept header). Without these handlers the catch-all below turns each of them
     * into a 500 and logs it as a server error.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException error,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(errorBody("Method not allowed", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException error,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(errorBody("Content-Type must be application/json", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotAcceptable(
        HttpMediaTypeNotAcceptableException error,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
            .body(errorBody("Not acceptable", request.getRequestURI()));
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
