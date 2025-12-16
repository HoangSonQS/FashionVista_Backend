package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginAdmin(LoginRequest request);

    /**
     * Xác thực email bằng token
     * @param token Token xác thực
     * @return true nếu xác thực thành công
     */
    boolean verifyEmail(String token);

    /**
     * Gửi lại email xác thực
     * @param email Email của user
     */
    void resendVerificationEmail(String email);

    /**
     * Gửi email đặt lại mật khẩu
     * @param email Email của user
     */
    void forgotPassword(String email);

    /**
     * Đặt lại mật khẩu bằng token
     * @param token Token reset
     * @param newPassword Mật khẩu mới
     */
    void resetPassword(String token, String newPassword);
}


