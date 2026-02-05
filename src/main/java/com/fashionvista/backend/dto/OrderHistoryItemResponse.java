package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderHistoryItemResponse {
    String field;
    String oldValue;
    String newValue;
    String actor;
    String note;
    LocalDateTime createdAt;
}

