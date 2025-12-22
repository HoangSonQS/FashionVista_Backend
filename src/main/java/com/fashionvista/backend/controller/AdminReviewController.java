package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminReviewOverviewResponse;
import com.fashionvista.backend.dto.AdminReviewResponse;
import com.fashionvista.backend.dto.AdminReviewTopProductResponse;
import com.fashionvista.backend.dto.AdminReviewTrendPoint;
import com.fashionvista.backend.service.AdminReviewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @GetMapping
    public Page<AdminReviewResponse> getAll(
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) Integer rating,
        @RequestParam(required = false) String search,
        Pageable pageable
    ) {
        return adminReviewService.getAll(productId, rating, search, pageable);
    }

    @DeleteMapping("/{id}")
    public void deleteReview(@PathVariable Long id) {
        adminReviewService.deleteReview(id);
    }

    @GetMapping("/analytics/overview")
    public AdminReviewOverviewResponse getOverview() {
        return adminReviewService.getOverview();
    }

    @GetMapping("/analytics/trend")
    public List<AdminReviewTrendPoint> getTrend(@RequestParam(defaultValue = "30") int days) {
        return adminReviewService.getTrend(days);
    }

    @GetMapping("/analytics/top-products")
    public List<AdminReviewTopProductResponse> getTopProducts(@RequestParam(defaultValue = "5") int limit) {
        return adminReviewService.getTopProducts(limit);
    }
}


