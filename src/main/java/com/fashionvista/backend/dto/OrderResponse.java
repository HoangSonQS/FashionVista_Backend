package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderResponse {

    Long id;
    String orderNumber;
    OrderStatus status;
    PaymentMethod paymentMethod;
    PaymentStatus paymentStatus;
    BigDecimal subtotal;
    BigDecimal shippingFee;
    BigDecimal discount;
    BigDecimal total;
    LocalDateTime createdAt;
    List<OrderItemResponse> items;
}

