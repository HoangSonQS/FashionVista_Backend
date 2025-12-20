package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.RefundMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class PartialRefundRequest {

    @NotNull(message = "Số tiền hoàn không được để trống")
    @Positive(message = "Số tiền hoàn phải lớn hơn 0")
    private BigDecimal amount;

    @NotNull(message = "Phương thức hoàn tiền không được để trống")
    private RefundMethod refundMethod;

    private String reason; // Lý do hoàn tiền

    private List<Long> itemIds; // Danh sách OrderItem IDs cần hoàn (null = hoàn toàn bộ)
}

