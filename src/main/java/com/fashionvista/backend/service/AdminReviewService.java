package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminReviewOverviewResponse;
import com.fashionvista.backend.dto.AdminReviewResponse;
import com.fashionvista.backend.dto.AdminReviewTopProductResponse;
import com.fashionvista.backend.dto.AdminReviewTrendPoint;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminReviewService {

    Page<AdminReviewResponse> getAll(Long productId, Integer rating, String search, Pageable pageable);

    void deleteReview(Long id);

    AdminReviewOverviewResponse getOverview();

    List<AdminReviewTrendPoint> getTrend(int days);

    List<AdminReviewTopProductResponse> getTopProducts(int limit);
}


