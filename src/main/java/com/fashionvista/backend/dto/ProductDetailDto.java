package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductDetailDto {

    Long id;
    String name;
    String slug;
    String description;
    String shortDescription;
    BigDecimal price;
    BigDecimal compareAtPrice;
    String status;
    boolean featured;
    String category;
    List<String> tags;
    List<String> sizes;
    List<String> colors;
    List<ProductImageDto> images;
    List<ProductVariantDto> variants;
}

