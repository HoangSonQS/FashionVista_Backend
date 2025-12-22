package com.fashionvista.backend.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminReviewTrendPoint {

    LocalDate date;
    long count;
    double avgRating;
}


