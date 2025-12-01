package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewSummaryResponse {

    Long id;
    Long productId;
    String productName;
    String productSlug;
    Integer rating;
    String comment;
    LocalDateTime createdAt;
}

