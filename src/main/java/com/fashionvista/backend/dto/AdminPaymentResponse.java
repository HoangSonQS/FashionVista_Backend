package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminPaymentResponse {
    Long id;
    Long orderId;
    String orderNumber;
    PaymentMethod paymentMethod;
    PaymentStatus paymentStatus;
    BigDecimal amount;
    BigDecimal refundAmount;
    String transactionId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}



