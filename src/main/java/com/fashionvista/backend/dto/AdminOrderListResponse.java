package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminOrderListResponse {

    Long id;
    String orderNumber;
    String customerName;
    String customerEmail;
    String customerPhone;
    OrderStatus status;
    PaymentMethod paymentMethod;
    PaymentStatus paymentStatus;
    BigDecimal total;
    LocalDateTime createdAt;
    Integer itemCount;
}

