package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.RefundMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResponse {

    private Long id;
    private String orderNumber;
    private BigDecimal amount;
    private RefundMethod refundMethod;
    private String reason;
    private List<Long> refundedItemIds;
    private String refundedBy;
    private LocalDateTime createdAt;
}

