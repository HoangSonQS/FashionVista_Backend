package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.ForgotPasswordRequest;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.ResetPasswordRequest;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        boolean verified = authService.verifyEmail(token);
        Map<String, String> response = new HashMap<>();
        if (verified) {
            response.put("message", "Email đã được xác thực thành công.");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "Xác thực email thất bại.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerificationEmail(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã gửi lại email xác thực.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã gửi email đặt lại mật khẩu (nếu email tồn tại).");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đặt lại mật khẩu thành công.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<com.fashionvista.backend.dto.RefreshTokenResponse> refreshToken(
            @Valid @RequestBody com.fashionvista.backend.dto.RefreshTokenRequest request) {
        com.fashionvista.backend.dto.RefreshTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestParam(required = false) String refreshToken) {
        authService.logout(refreshToken);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đăng xuất thành công.");
        return ResponseEntity.ok(response);
    }
}
