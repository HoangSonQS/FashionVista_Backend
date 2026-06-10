package com.fashionvista.backend.service;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.fashionvista.backend.config.AuthSecurityProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CookieService {

    private final AuthSecurityProperties properties;

    public void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        AuthSecurityProperties.RefreshToken config = properties.getRefreshToken();
        ResponseCookie cookie = ResponseCookie.from(config.getCookieName(), refreshToken)
                .httpOnly(config.isHttpOnly())
                .secure(config.isSecure())
                .sameSite(config.getSameSite())
                .path(config.getCookiePath())
                .maxAge(Duration.ofSeconds(config.getTtlSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        AuthSecurityProperties.RefreshToken config = properties.getRefreshToken();
        ResponseCookie cookie = ResponseCookie.from(config.getCookieName(), "")
                .httpOnly(config.isHttpOnly())
                .secure(config.isSecure())
                .sameSite(config.getSameSite())
                .path(config.getCookiePath())
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String getRefreshCookieName() {
        return properties.getRefreshToken().getCookieName();
    }
}
