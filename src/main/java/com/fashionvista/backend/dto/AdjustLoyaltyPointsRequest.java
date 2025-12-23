package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdjustLoyaltyPointsRequest {
    @NotNull(message = "User ID không được để trống.")
    private Long userId;

    @NotNull(message = "Số điểm không được để trống.")
    private Integer points; // Có thể âm để trừ điểm

    private String description; // Mô tả lý do điều chỉnh
}

