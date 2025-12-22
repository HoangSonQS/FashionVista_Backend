package com.fashionvista.backend.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminReviewOverviewResponse {

    long totalReviews;
    double avgRating;
    double positiveRate;
    double negativeRate;
    Map<Integer, Long> ratingCounts;
}


