package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class AddLoyaltyPointsRequest {

    @NotNull(message = "Số điểm không được để trống")
    Integer points; // Có thể âm để trừ điểm

    @NotNull(message = "Loại giao dịch không được để trống")
    String transactionType; // EARNED, SPENT, MANUAL_ADJUST, EXPIRED

    String source; // "ORDER_123", "ADMIN_ADJUST", "PROMOTION_XYZ"

    String description; // Mô tả chi tiết
}

