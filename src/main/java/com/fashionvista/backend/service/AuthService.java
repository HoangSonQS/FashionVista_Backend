package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    AuthResponse loginAdmin(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Xác thực email bằng token
     * 
     * @param token Token xác thực
     * @return true nếu xác thực thành công
     */
    boolean verifyEmail(String token);

    /**
     * Gửi lại email xác thực
     * 
     * @param email Email của user
     */
    void resendVerificationEmail(String email);

    /**
     * Gửi email đặt lại mật khẩu
     * 
     * @param email Email của user
     */
    void forgotPassword(String email);

    /**
     * Đặt lại mật khẩu bằng token
     * 
     * @param token       Token reset
     * @param newPassword Mật khẩu mới
     */
    void resetPassword(String token, String newPassword);

    /**
     * Làm mới token khi Access Token hết hạn
     * 
     * @param request RefreshTokenRequest chứa refresh token cũ
     * @return RefreshTokenResponse chứa bộ đôi token mới
     */
    com.fashionvista.backend.dto.RefreshTokenResponse refreshToken(
            com.fashionvista.backend.dto.RefreshTokenRequest request);

    /**
     * Đăng xuất người dùng (xóa Refresh Token)
     * 
     * @param request HttpServletRequest để lấy thông tin user hiện tại (nếu cần)
     *                hoặc token
     */
    void logout(String refreshToken);
}
