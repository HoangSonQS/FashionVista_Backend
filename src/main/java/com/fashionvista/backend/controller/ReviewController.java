package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.CreateReviewRequest;
import com.fashionvista.backend.dto.ReviewSummaryResponse;
import com.fashionvista.backend.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Test endpoint để kiểm tra controller có được load không.
     */
    @GetMapping("/reviews/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("ReviewController is working!");
    }

    /**
     * Tạo review cho sản phẩm (user đã đăng nhập, đã mua).
     */
    @PostMapping("/reviews")
    public ResponseEntity<ReviewSummaryResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {
        ReviewSummaryResponse response = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lấy danh sách review theo productId (public).
     */
    @GetMapping("/reviews/product/{productId}")
    public ResponseEntity<List<ReviewSummaryResponse>> getProductReviews(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getProductReviews(productId));
    }

    /**
     * Lấy danh sách review của current user (trang \"Đánh giá của tôi\").
     */
    @GetMapping("/me/reviews")
    public ResponseEntity<List<ReviewSummaryResponse>> getMyReviews() {
        return ResponseEntity.ok(reviewService.getMyReviews());
    }
}


