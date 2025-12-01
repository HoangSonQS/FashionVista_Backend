package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderItemResponse {

    Long id;
    String productName;
    String productSlug;
    String productImage;
    String size;
    String color;
    Integer quantity;
    BigDecimal price;
    BigDecimal subtotal;
}

