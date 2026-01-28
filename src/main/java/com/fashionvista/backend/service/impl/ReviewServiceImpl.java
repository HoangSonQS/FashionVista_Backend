package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.CreateReviewRequest;
import com.fashionvista.backend.dto.ReviewSummaryResponse;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.Review;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.repository.ReviewRepository;
import com.fashionvista.backend.service.ReviewService;
import com.fashionvista.backend.service.UserContextService;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserContextService userContextService;

    @Override
    @Transactional
    public ReviewSummaryResponse createReview(CreateReviewRequest request) {
        User user = userContextService.getCurrentUser();
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        // Kiểm tra user đã có đơn hàng chứa sản phẩm này và đơn hàng đã giao thành công
        boolean hasPurchased = hasDeliveredOrderWithProduct(user, product);
        if (!hasPurchased) {
            throw new IllegalArgumentException("Bạn chỉ có thể đánh giá sản phẩm đã mua.");
        }

        // Tạo review mới (Cho phép nhiều review)

        // Tạo review mới
        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);
        return toSummary(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewSummaryResponse> getProductReviews(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        return product.getReviews().stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewSummaryResponse> getMyReviews() {
        User user = userContextService.getCurrentUser();
        return reviewRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toSummary)
                .toList();
    }

    private boolean hasDeliveredOrderWithProduct(User user, Product product) {
        List<Order> orders = orderRepository.findByUserOrderByCreatedAtDesc(user);
        EnumSet<OrderStatus> validStatuses = EnumSet.of(OrderStatus.DELIVERED);
        return orders.stream()
                .filter(order -> validStatuses.contains(order.getStatus()))
                .anyMatch(order -> order.getItems().stream()
                        .anyMatch(item -> item.getProduct().getId().equals(product.getId())));
    }

    private ReviewSummaryResponse toSummary(Review review) {
        return ReviewSummaryResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .productSlug(review.getProduct().getSlug())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
