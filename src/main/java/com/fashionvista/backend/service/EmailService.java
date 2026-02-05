package com.fashionvista.backend.service;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.User;

/**
 * Service xử lý gửi email
 */
public interface EmailService {

    /**
     * Gửi email xác thực tài khoản
     * 
     * @param user              User cần xác thực
     * @param verificationToken Token xác thực
     */
    void sendVerificationEmail(User user, String verificationToken);

    /**
     * Gửi email xác nhận đơn hàng
     * 
     * @param order Đơn hàng đã đặt
     */
    void sendOrderConfirmationEmail(Order order);

    /**
     * Gửi email thông báo thay đổi trạng thái đơn hàng
     * 
     * @param order     Đơn hàng có thay đổi trạng thái
     * @param oldStatus Trạng thái cũ
     * @param newStatus Trạng thái mới
     */
    void sendOrderStatusUpdateEmail(Order order, String oldStatus, String newStatus);

    /**
     * Gửi email reset mật khẩu
     * 
     * @param user       User cần reset mật khẩu
     * @param resetToken Token reset mật khẩu
     */
    void sendPasswordResetEmail(User user, String resetToken);

    /**
     * Gửi email xác nhận thanh toán
     * 
     * @param order Đơn hàng đã thanh toán
     */
    void sendPaymentConfirmationEmail(Order order);

    /**
     * Gửi email nhắc nhở giỏ hàng bị bỏ quên
     * 
     * @param email Email người nhận
     * @param cart  Giỏ hàng
     */
    void sendAbandonedCartEmail(String email, com.fashionvista.backend.entity.Cart cart);
}
