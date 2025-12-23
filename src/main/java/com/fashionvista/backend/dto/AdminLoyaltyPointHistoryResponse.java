package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminLoyaltyPointHistoryResponse {
    Long id;
    Long userId;
    String userEmail;
    String userFullName;
    Integer points;
    Integer balanceAfter;
    String transactionType; // EARNED, SPENT, MANUAL_ADJUST, EXPIRED
    String source;
    String description;
    LocalDateTime createdAt;
    String createdByName; // Admin name nếu manual adjust
}

