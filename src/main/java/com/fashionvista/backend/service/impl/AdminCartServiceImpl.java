package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminCartListResponse;
import com.fashionvista.backend.entity.Cart;
import com.fashionvista.backend.entity.CartItem;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.CartRepository;
import com.fashionvista.backend.service.AdminCartService;
import com.fashionvista.backend.service.EmailService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCartServiceImpl implements AdminCartService {

    private final CartRepository cartRepository;
    // EmailService might be needed for reminders
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminCartListResponse> getAdminCarts(String search, Boolean isAbandoned, Pageable pageable) {
        Specification<Cart> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter Active vs Abandoned
            if (isAbandoned != null) {
                LocalDateTime threshold = LocalDateTime.now().minusHours(24);
                if (isAbandoned) {
                    // Abandoned: updatedAt < 24h ago
                    predicates.add(cb.lessThan(root.get("updatedAt"), threshold));
                } else {
                    // Active: updatedAt >= 24h ago
                    predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), threshold));
                }
            }

            // Search by User Email or Name
            if (search != null && !search.trim().isEmpty()) {
                String likePattern = "%" + search.trim().toLowerCase() + "%";
                Join<Cart, User> userJoin = root.join("user", JoinType.LEFT);

                Predicate hasUser = cb.isNotNull(root.get("user"));
                Predicate matchEmail = cb.like(cb.lower(userJoin.get("email")), likePattern);
                Predicate matchName = cb.like(cb.lower(userJoin.get("fullName")), likePattern);

                // Also search sessionId for guests
                Predicate matchSession = cb.like(cb.lower(root.get("sessionId")), likePattern);

                predicates.add(cb.or(
                        cb.and(hasUser, cb.or(matchEmail, matchName)),
                        matchSession));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Cart> cartPage = cartRepository.findAll(spec, pageable);

        return cartPage.map(this::toAdminCartListResponse);
    }

    @Override
    public void sendCartReminder(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        if (cart.getUser() == null) {
            throw new RuntimeException("Cannot send email to guest cart");
        }

        log.info("Sending abandoned cart reminder to user: {}", cart.getUser().getEmail());
        emailService.sendAbandonedCartEmail(cart.getUser().getEmail(), cart);
    }

    @Override
    @Transactional(readOnly = true)
    public com.fashionvista.backend.dto.CartResponse getCartDetail(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cart not found"));
        return toCartResponse(cart);
    }

    private com.fashionvista.backend.dto.CartResponse toCartResponse(Cart cart) {
        List<com.fashionvista.backend.dto.CartItemResponse> items = cart.getItems().stream()
                .map(item -> com.fashionvista.backend.dto.CartItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                        .productName(item.getProduct().getName())
                        .productSlug(item.getProduct().getSlug())
                        .thumbnailUrl(item.getProduct().getImages().stream()
                                .findFirst()
                                .map(image -> image.getUrl())
                                .orElse(null))
                        .size(item.getVariant() != null ? item.getVariant().getSize() : null)
                        .color(item.getVariant() != null ? item.getVariant().getColor() : null)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getPrice())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        BigDecimal subtotal = items.stream()
                .map(com.fashionvista.backend.dto.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Admin likely wants to see raw cart data, but let's keep logic consistent
        BigDecimal shippingFee = subtotal.compareTo(BigDecimal.valueOf(2000000)) >= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(30000);

        return com.fashionvista.backend.dto.CartResponse.builder()
                .id(cart.getId())
                .items(items)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .total(subtotal.add(shippingFee))
                .build();
    }

    private AdminCartListResponse toAdminCartListResponse(Cart cart) {
        int itemsCount = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();
        BigDecimal totalValue = cart.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isAbandoned = cart.getUpdatedAt().isBefore(LocalDateTime.now().minusHours(24));

        return AdminCartListResponse.builder()
                .id(cart.getId())
                .userId(cart.getUser() != null ? cart.getUser().getId() : null)
                .userName(cart.getUser() != null ? cart.getUser().getFullName() : "Guest")
                .userEmail(cart.getUser() != null ? cart.getUser().getEmail() : null)
                .sessionId(cart.getSessionId())
                .itemsCount(itemsCount)
                .totalValue(totalValue)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .isAbandoned(isAbandoned)
                .build();
    }
}
