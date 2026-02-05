package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminReviewOverviewResponse;
import com.fashionvista.backend.dto.AdminReviewResponse;
import com.fashionvista.backend.dto.AdminReviewTopProductResponse;
import com.fashionvista.backend.dto.AdminReviewTrendPoint;
import com.fashionvista.backend.entity.Review;
import com.fashionvista.backend.repository.ReviewRepository;
import com.fashionvista.backend.service.AdminReviewService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> getAll(Long productId, Integer rating, String search, Pageable pageable) {
        return reviewRepository.searchAdminReviews(
                productId,
                rating,
                search != null ? search.trim() : null,
                pageable
            )
            .map(this::toAdminResponse);
    }

    @Override
    @Transactional
    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewOverviewResponse getOverview() {
        List<Object[]> rows = reviewRepository.aggregateRatingCounts();
        long total = 0;
        double sum = 0;
        long positive = 0;
        long negative = 0;
        Map<Integer, Long> ratingCounts = rows.stream().collect(Collectors.toMap(
            row -> ((Number) row[0]).intValue(),
            row -> ((Number) row[1]).longValue()
        ));

        for (Map.Entry<Integer, Long> e : ratingCounts.entrySet()) {
            int rating = e.getKey();
            long count = e.getValue();
            total += count;
            sum += rating * count;
            if (rating >= 4) {
                positive += count;
            } else if (rating <= 2) {
                negative += count;
            }
        }

        double avgRating = total > 0 ? sum / total : 0.0;
        double positiveRate = total > 0 ? (double) positive / total : 0.0;
        double negativeRate = total > 0 ? (double) negative / total : 0.0;

        return AdminReviewOverviewResponse.builder()
            .totalReviews(total)
            .avgRating(avgRating)
            .positiveRate(positiveRate)
            .negativeRate(negativeRate)
            .ratingCounts(ratingCounts)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminReviewTrendPoint> getTrend(int days) {
        int safeDays = days <= 0 ? 30 : days;
        LocalDate fromDate = LocalDate.now().minusDays(safeDays - 1L);
        return reviewRepository.aggregateTrend(fromDate).stream()
            .map(row -> AdminReviewTrendPoint.builder()
                .date((LocalDate) row[0])
                .count(((Number) row[1]).longValue())
                .avgRating(((Number) row[2]).doubleValue())
                .build())
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminReviewTopProductResponse> getTopProducts(int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        return reviewRepository.aggregateTopProducts(safeLimit).stream()
            .map(row -> AdminReviewTopProductResponse.builder()
                .productId(((Number) row[0]).longValue())
                .productName((String) row[1])
                .productSlug((String) row[2])
                .thumbnailUrl((String) row[3])
                .reviewCount(((Number) row[4]).longValue())
                .avgRating(((Number) row[5]).doubleValue())
                .negativeRate(((Number) row[6]).doubleValue())
                .build())
            .toList();
    }

    private AdminReviewResponse toAdminResponse(Review review) {
        return AdminReviewResponse.builder()
            .id(review.getId())
            .productId(review.getProduct().getId())
            .productName(review.getProduct().getName())
            .productSlug(review.getProduct().getSlug())
            .userId(review.getUser().getId())
            .userName(review.getUser().getFullName())
            .userEmail(review.getUser().getEmail())
            .rating(review.getRating())
            .comment(review.getComment())
            .createdAt(review.getCreatedAt())
            .build();
    }
}


