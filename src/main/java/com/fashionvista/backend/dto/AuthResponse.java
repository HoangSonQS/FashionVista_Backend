package com.fashionvista.backend.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String token;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserResponse user;

    public AuthResponse(String token, String refreshToken, UserResponse user) {
        this.token = token;
        this.accessToken = token;
        this.refreshToken = refreshToken;
        this.expiresIn = 600;
        this.user = user;
    }
}
