package com.fashionvista.backend.service;

import com.fashionvista.backend.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginActivityService {

    /**
     * Ghi lại hoạt động đăng nhập thành công.
     */
    void recordSuccessfulLogin(User user, HttpServletRequest request);

    /**
     * Ghi lại hoạt động đăng nhập thất bại.
     * @param user User đã được tìm thấy (không null)
     * @param failureReason Lý do thất bại
     * @param request HttpServletRequest để lấy IP và User-Agent
     */
    void recordFailedLogin(User user, String failureReason, HttpServletRequest request);

    /**
     * Kiểm tra hiện tại hệ thống có đang ghi log đăng nhập hay không.
     */
    boolean isLoginActivityEnabled();

    /**
     * Bật/tắt ghi log đăng nhập ở runtime.
     */
    void setLoginActivityEnabled(boolean enabled);
}

