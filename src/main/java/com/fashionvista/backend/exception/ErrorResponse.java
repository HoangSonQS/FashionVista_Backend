package com.fashionvista.backend.exception;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, Object> details
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ErrorResponse withDetails(HttpStatus status, String message, String path, Map<String, Object> details) {
        return new ErrorResponse(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, path, details);
    }
}



