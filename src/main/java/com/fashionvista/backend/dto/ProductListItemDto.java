package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductListItemDto {

    Long id;
    String name;
    String slug;
    BigDecimal price;
    BigDecimal compareAtPrice;
    String status;
    boolean featured;
    String thumbnailUrl;
}

