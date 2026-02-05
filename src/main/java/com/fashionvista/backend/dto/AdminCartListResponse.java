package com.fashionvista.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCartListResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String sessionId; // For guest carts
    private int itemsCount;
    private BigDecimal totalValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonProperty("isAbandoned")
    private boolean isAbandoned;
}
