package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminReviewResponse {

    Long id;
    Long productId;
    String productName;
    String productSlug;
    Long userId;
    String userName;
    String userEmail;
    Integer rating;
    String comment;
    LocalDateTime createdAt;
}


