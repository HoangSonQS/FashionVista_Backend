package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CartItemResponse {

    Long id;
    Long productId;
    Long variantId;
    String productName;
    String productSlug;
    String thumbnailUrl;
    String size;
    String color;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal subtotal;
}