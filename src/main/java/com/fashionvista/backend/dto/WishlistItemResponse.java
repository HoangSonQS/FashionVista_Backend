package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WishlistItemResponse {

    Long id;
    Long productId;
    String productName;
    String productSlug;
    String productImage;
    BigDecimal price;
    LocalDateTime addedAt;
}

