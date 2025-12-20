package com.fashionvista.backend.service;

import java.math.BigDecimal;

/**
 * Service xử lý nghiệp vụ voucher.
 */
public interface VoucherService {

    /**
     * Validate voucher và tính số tiền giảm cho subtotal.
     * Chỉ validate và tính discount, KHÔNG tăng usedCount.
     *
     * @param code     mã voucher
     * @param subtotal tổng tiền hàng (chưa gồm ship, chưa trừ giảm giá)
     * @return số tiền giảm (>= 0)
     * @throws IllegalArgumentException nếu voucher không hợp lệ
     */
    BigDecimal validateAndCalculateDiscount(String code, BigDecimal subtotal);

    /**
     * Áp dụng voucher (tăng usedCount) sau khi đơn hàng được tạo thành công.
     *
     * @param code mã voucher
     * @throws IllegalArgumentException nếu mã voucher không tồn tại
     */
    void applyVoucher(String code);
}


