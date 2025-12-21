package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminProductVariantResponse {
    Long id;
    Long productId;
    String productName;
    String productSlug;
    String size;
    String color;
    String sku;
    BigDecimal price;
    Integer stock;
    boolean active;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

