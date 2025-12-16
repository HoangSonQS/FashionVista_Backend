package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.CreateReviewRequest;
import com.fashionvista.backend.dto.ReviewSummaryResponse;
import java.util.List;

public interface ReviewService {

    /**
     * Tạo review cho sản phẩm. Chỉ user đã mua mới được review.
     */
    ReviewSummaryResponse createReview(CreateReviewRequest request);

    /**
     * Lấy danh sách review theo sản phẩm (public).
     */
    List<ReviewSummaryResponse> getProductReviews(Long productId);

    /**
     * Lấy danh sách review của current user (trang \"Đánh giá của tôi\").
     */
    List<ReviewSummaryResponse> getMyReviews();
}


