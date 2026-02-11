package com.example.rate_limiter.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Bad Request")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisConnectionFailure(RedisConnectionFailureException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .error("Service Unavailable")
                        .message("Redis cache service is currently unavailable. Please try again later.")
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(io.lettuce.core.RedisConnectionException.class)
    public ResponseEntity<ErrorResponse> handleLettuceRedisConnectionException(io.lettuce.core.RedisConnectionException ex, HttpServletRequest request) {
        String message = "Unable to connect to Redis: ";
        if (ex.getMessage().contains("NOAUTH")) {
            message += "Redis authentication failed. Check credentials.";
        } else if (ex.getMessage().contains("connect")) {
            message += "Cannot reach Redis server. Check host and port configuration.";
        } else {
            message += "Redis connection error. Check configuration.";
        }
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .error("Service Unavailable")
                        .message(message)
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred";
        String error = "Internal Server Error";
        
        // Provide more specific error messages
        if (message.contains("Connection refused")) {
            message = "Backend service temporarily unavailable";
        } else if (message.contains("timeout")) {
            message = "Request timeout - service is taking too long to respond";
        } else if (message.contains("mail")) {
            message = "Email service is not properly configured";
        } else if (message.contains("sms")) {
            message = "SMS service is not properly configured";
        }
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error(error)
                        .message(message)
                        .path(request.getRequestURI())
                        .build());
    }
}
