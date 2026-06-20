package com.fashionvista.backend.dto.sapo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoProductDto {
    Long id;
    String name;
    String sku;
    String slug;
    String status;
    String category;
    BigDecimal price;
    BigDecimal compareAtPrice;
    List<SapoVariantDto> variants;
    LocalDateTime updatedAt;
}
