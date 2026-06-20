package com.fashionvista.backend.exception;

import com.fashionvista.backend.dto.sapo.SapoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(1)
@RestControllerAdvice(basePackages = "com.fashionvista.backend.controller.sapo")
public class SapoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SapoExceptionHandler.class);

    @ExceptionHandler(SapoNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public SapoResponse<Void> handleNotFound(SapoNotFoundException ex) {
        return SapoResponse.error(ex.getMessage());
    }

    @ExceptionHandler(SapoDuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public SapoResponse<Void> handleDuplicate(SapoDuplicateException ex) {
        return SapoResponse.error(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SapoResponse<Void> handleIllegalArg(IllegalArgumentException ex) {
        return SapoResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SapoResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        return SapoResponse.error(msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public SapoResponse<Void> handleGeneric(Exception ex) {
        log.error("[Sapo] Unhandled exception: {}", ex.getMessage(), ex);
        return SapoResponse.error("Lỗi hệ thống");
    }
}
