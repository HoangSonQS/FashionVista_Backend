package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductVariantDto {

    Long id;
    String size;
    String color;
    String sku;
    BigDecimal price;
    Integer stock;
    boolean active;
}

