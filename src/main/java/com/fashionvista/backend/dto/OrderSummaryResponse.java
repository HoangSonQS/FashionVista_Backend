package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderSummaryResponse {

    Long id;
    String orderNumber;
    OrderStatus status;
    PaymentStatus paymentStatus;
    BigDecimal total;
    Integer itemCount;
    LocalDateTime createdAt;
}

