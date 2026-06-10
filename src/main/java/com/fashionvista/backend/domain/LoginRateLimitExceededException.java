package com.fashionvista.backend.domain;

public class LoginRateLimitExceededException extends RuntimeException {
    public LoginRateLimitExceededException(String message) {
        super(message);
    }
}
