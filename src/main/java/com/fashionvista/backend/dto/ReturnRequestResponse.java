package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.RefundMethod;
import com.fashionvista.backend.entity.ReturnStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReturnRequestResponse {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private ReturnStatus status;
    private String reason;
    private String note;
    private List<String> evidenceUrls;
    private BigDecimal refundAmount;
    private RefundMethod refundMethod;
    private String adminNote;
    private List<ReturnItemDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime refundedAt;
}


