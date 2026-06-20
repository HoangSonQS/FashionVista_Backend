package com.fashionvista.backend.dto.sapo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoVariantDto {
    Long id;
    String sku;
    String size;
    String color;
    BigDecimal price;
    BigDecimal compareAtPrice;
    Integer stock;
    boolean active;
}
