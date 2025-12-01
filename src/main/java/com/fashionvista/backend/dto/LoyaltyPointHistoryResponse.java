package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoyaltyPointHistoryResponse {

    Long id;
    Integer points;
    Integer balanceAfter;
    String transactionType;
    String source;
    String description;
    LocalDateTime createdAt;
    String createdByName; // Admin name nếu manual adjust
}

