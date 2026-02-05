package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminReviewTopProductResponse {

    Long productId;
    String productName;
    String productSlug;
    String thumbnailUrl;
    long reviewCount;
    double avgRating;
    double negativeRate;
}


