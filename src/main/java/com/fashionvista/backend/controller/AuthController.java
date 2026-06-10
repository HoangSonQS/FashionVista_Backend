package com.fashionvista.backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.ForgotPasswordRequest;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RefreshTokenRequest;
import com.fashionvista.backend.dto.RefreshTokenResponse;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.dto.ResetPasswordRequest;
import com.fashionvista.backend.dto.UserResponse;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.service.AuthService;
import com.fashionvista.backend.service.CookieService;
import com.fashionvista.backend.service.UserContextService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final UserContextService userContextService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request);
        if (cookieService != null && response.getRefreshToken() != null) {
            cookieService.addRefreshCookie(httpResponse, response.getRefreshToken());
            response.setRefreshToken(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request, httpRequest);
        if (cookieService != null && response.getRefreshToken() != null) {
            cookieService.addRefreshCookie(httpResponse, response.getRefreshToken());
            response.setRefreshToken(null);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        boolean verified = authService.verifyEmail(token);
        Map<String, String> response = new HashMap<>();
        response.put("message", verified ? "Email da duoc xac thuc thanh cong." : "Xac thuc email that bai.");
        return verified ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerificationEmail(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Da gui lai email xac thuc.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Da gui email dat lai mat khau neu email ton tai.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Dat lai mat khau thanh cong.");
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/refresh", "/refresh-token"})
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletResponse httpResponse) {
        RefreshTokenResponse response;
        if (refreshTokenCookie == null && request != null) {
            response = authService.refreshToken(request);
        } else {
            response = authService.refreshToken(refreshTokenCookie);
        }
        if (cookieService != null && response.getRefreshToken() != null) {
            cookieService.addRefreshCookie(httpResponse, response.getRefreshToken());
            response.setRefreshToken(null);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestParam(required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        String accessToken = extractBearer(authorization);
        String effectiveRefreshToken = refreshTokenCookie != null ? refreshTokenCookie : refreshToken;
        if (accessToken == null) {
            authService.logout(effectiveRefreshToken);
        } else {
            authService.logout(accessToken, effectiveRefreshToken);
        }
        if (cookieService != null) {
            cookieService.clearRefreshCookie(httpResponse);
        }
        Map<String, String> response = new HashMap<>();
        response.put("message", "Dang xuat thanh cong.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse httpResponse) {
        authService.logoutAll(Long.valueOf(jwt.getSubject()));
        cookieService.clearRefreshCookie(httpResponse);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Dang xuat tat ca thiet bi thanh cong.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        User user = userContextService.getCurrentUser();
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    private String extractBearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
