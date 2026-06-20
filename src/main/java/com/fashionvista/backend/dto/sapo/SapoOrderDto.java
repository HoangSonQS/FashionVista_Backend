package com.fashionvista.backend.dto.sapo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoOrderDto {
    String orderNumber;
    String status;
    String paymentMethod;
    String paymentStatus;
    String transactionId;
    SapoOrderCustomerDto customer;
    String shippingAddress;
    List<SapoOrderItemDto> items;
    BigDecimal subtotal;
    BigDecimal shippingFee;
    BigDecimal discount;
    String voucherCode;
    BigDecimal total;
    String trackingNumber;
    String source;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
