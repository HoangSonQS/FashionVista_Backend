package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.ShippingMethod;
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
    ShippingMethod shippingMethod;
    String shippingAddress;
    String billingAddress;
    BigDecimal subtotal;
    BigDecimal shippingFee;
    BigDecimal discount;
    BigDecimal voucherDiscount;
    BigDecimal total;
    LocalDateTime createdAt;
    List<OrderItemResponse> items;
    String paymentUrl;
    String trackingNumber; // Mã vận đơn
    String trackingUrl; // Link tracking (GHN/GHTK)

    // Extra info
    String customerEmail;
    String customerPhone;
    String customerGroup;
    String transactionId;

    List<OrderHistoryItemResponse> history;
}

