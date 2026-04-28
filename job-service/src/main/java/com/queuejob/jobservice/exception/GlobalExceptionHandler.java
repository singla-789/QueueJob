package com.queuejob.jobservice.exception;

import com.queuejob.jobservice.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 — Job Not Found ──────────────────────────────────────────
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleJobNotFound(
            JobNotFoundException ex, HttpServletRequest request) {

        log.warn("Job not found: {}", ex.getMessage());

        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ── 409 — Invalid Job State ──────────────────────────────────────
    @ExceptionHandler(InvalidJobStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJobState(
            InvalidJobStateException ex, HttpServletRequest request) {

        log.warn("Invalid job state: {}", ex.getMessage());

        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ── 429 — Rate Limit Exceeded ────────────────────────────────────
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    // ── 400 — Validation Errors (@Valid) ─────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", fieldErrors);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .validationErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── 400 — Malformed JSON / type mismatch ─────────────────────────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body: {}", ex.getMessage());

        return buildResponse(HttpStatus.BAD_REQUEST,
                "Malformed JSON request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());
        log.warn("Type mismatch: {}", message);

        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    // ── 400 — Illegal argument ───────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());

        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ── 500 — Catch-all ──────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error", ex);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request);
    }

    // ── Helper ───────────────────────────────────────────────────────
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
