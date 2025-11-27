package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CartResponse {

    Long id;
    List<CartItemResponse> items;
    BigDecimal subtotal;
    BigDecimal shippingFee;
    BigDecimal total;
}

