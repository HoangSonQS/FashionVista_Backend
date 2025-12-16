package com.fashionvista.backend.service;

import java.math.BigDecimal;

/**
 * Service xử lý nghiệp vụ voucher.
 */
public interface VoucherService {

    /**
     * Validate voucher và tính số tiền giảm cho subtotal.
     *
     * @param code     mã voucher
     * @param subtotal tổng tiền hàng (chưa gồm ship, chưa trừ giảm giá)
     * @return số tiền giảm (>= 0)
     * @throws IllegalArgumentException nếu voucher không hợp lệ
     */
    BigDecimal validateAndCalculateDiscount(String code, BigDecimal subtotal);
}


