package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

@Value
@Builder
public class AdminLoyaltyPointStatsResponse {
    Long totalUsers; // Tổng số user có điểm
    Long totalPoints; // Tổng điểm của tất cả user
    Map<String, Long> pointsByTier; // Tổng điểm theo tier (BRONZE, SILVER, GOLD, PLATINUM)
    Map<String, Long> usersByTier; // Số lượng user theo tier
}

