package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WishlistItemResponse {

    Long id;
    Long productId;
    String productName;
    String productSlug;
    String thumbnailUrl;
    BigDecimal price;
    BigDecimal compareAtPrice;
}
