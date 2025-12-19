package com.fashionvista.backend.service;

import com.fashionvista.backend.entity.Order;

/**
 * Service xử lý quy tắc tích/tiêu điểm thưởng.
 * Hiện tại chủ yếu dùng cho admin quan sát (user chưa xem được điểm).
 */
public interface LoyaltyService {

    /**
     * Áp dụng quy tắc tích điểm cho một đơn hàng.
     * Quy tắc hiện tại: 1 điểm / 10.000₫, làm tròn xuống, chỉ tích khi đơn đã thanh toán thành công.
     * Hàm này phải idempotent cho cùng một đơn hàng (không cộng trùng nhiều lần).
     *
     * @param order Đơn hàng đã được thanh toán thành công
     */
    void awardPointsForOrder(Order order);
}




