package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.CartResponse;
import com.fashionvista.backend.dto.CheckoutRequest;
import com.fashionvista.backend.dto.OrderItemResponse;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.entity.Cart;
import com.fashionvista.backend.entity.CartItem;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.CartRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.CartService;
import com.fashionvista.backend.service.OrderService;
import com.fashionvista.backend.service.UserContextService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final UserContextService userContextService;

    private static final AtomicLong DAILY_SEQUENCE = new AtomicLong(0);

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Cart cart = cartRepository.findByUser(userContextService.getCurrentUser())
            .orElseThrow(() -> new IllegalArgumentException("Giỏ hàng trống."));

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng chưa có sản phẩm nào.");
        }

        // Validate stock
        cart.getItems().forEach(this::validateStock);

        String orderNumber = generateOrderNumber();
        Order order = Order.builder()
            .orderNumber(orderNumber)
            .user(userContextService.getCurrentUser())
            .status(com.fashionvista.backend.entity.OrderStatus.PENDING)
            .paymentMethod(request.getPaymentMethod())
            .paymentStatus(PaymentStatus.PENDING)
            .shippingAddress(buildShippingSnapshot(request))
            .subtotal(BigDecimal.ZERO)
            .shippingFee(BigDecimal.ZERO)
            .discount(BigDecimal.ZERO)
            .total(BigDecimal.ZERO)
            .build();

        List<OrderItem> orderItems = cart.getItems().stream()
            .map(item -> toOrderItem(order, item))
            .toList();

        order.setItems(orderItems);
        CartResponse cartResponse = cartService.getMyCart();

        order.setSubtotal(cartResponse.getSubtotal());
        order.setShippingFee(cartResponse.getShippingFee());
        order.setTotal(cartResponse.getTotal());
        order.setDiscount(BigDecimal.ZERO);

        Order saved = orderRepository.save(order);

        Payment payment = Payment.builder()
            .order(saved)
            .paymentMethod(request.getPaymentMethod())
            .paymentStatus(PaymentStatus.PENDING)
            .amount(saved.getTotal())
            .build();
        paymentRepository.save(payment);

        // Decrease stock
        cart.getItems().forEach(this::decreaseStock);

        cartService.clearCart();

        return toOrderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        return orderRepository.findByUserOrderByCreatedAtDesc(userContextService.getCurrentUser())
            .stream()
            .map(this::toOrderResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getUser().getId().equals(userContextService.getCurrentUser().getId())) {
            throw new IllegalArgumentException("Bạn không có quyền xem đơn hàng này.");
        }

        return toOrderResponse(order);
    }

    private void validateStock(CartItem item) {
        ProductVariant variant = item.getVariant();
        if (variant == null) {
            throw new IllegalArgumentException("Sản phẩm không có biến thể.");
        }
        if (variant.getStock() < item.getQuantity()) {
            throw new IllegalArgumentException("Sản phẩm " + variant.getSku() + " không đủ tồn kho.");
        }
    }

    private void decreaseStock(CartItem item) {
        ProductVariant variant = productVariantRepository.findById(item.getVariant().getId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm."));
        int remaining = variant.getStock() - item.getQuantity();
        if (remaining < 0) {
            throw new IllegalArgumentException("Sản phẩm " + variant.getSku() + " không đủ tồn kho.");
        }
        variant.setStock(remaining);
        try {
            productVariantRepository.save(variant);
        } catch (OptimisticLockingFailureException ex) {
            throw new IllegalStateException("Sản phẩm vừa được cập nhật. Vui lòng thử lại.", ex);
        }
    }

    private OrderItem toOrderItem(Order order, CartItem item) {
        return OrderItem.builder()
            .order(order)
            .product(item.getProduct())
            .variant(item.getVariant())
            .productName(item.getProduct().getName())
            .productImage(item.getProduct().getImages().stream()
                .findFirst()
                .map(image -> image.getUrl())
                .orElse(null))
            .price(item.getPrice())
            .quantity(item.getQuantity())
            .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .build();
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> OrderItemResponse.builder()
                .id(item.getId())
                .productName(item.getProductName())
                .productSlug(item.getProduct().getSlug())
                .size(item.getVariant() != null ? item.getVariant().getSize() : null)
                .color(item.getVariant() != null ? item.getVariant().getColor() : null)
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getSubtotal())
                .build())
            .toList();

        return OrderResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .paymentMethod(order.getPaymentMethod())
            .paymentStatus(order.getPaymentStatus())
            .subtotal(order.getSubtotal())
            .shippingFee(order.getShippingFee())
            .discount(order.getDiscount())
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .items(itemResponses)
            .build();
    }

    private String generateOrderNumber() {
        long sequence = DAILY_SEQUENCE.incrementAndGet();
        return "ORD-" + Instant.now().toString().substring(0, 10).replace("-", "") + "-" + sequence;
    }

    private String buildShippingSnapshot(CheckoutRequest request) {
        return String.format(
            "%s, %s, %s, %s (Phone: %s)%s",
            request.getAddress(),
            request.getWard(),
            request.getDistrict(),
            request.getCity(),
            request.getPhone(),
            request.getNotes() != null && !request.getNotes().isBlank()
                ? " | Notes: " + request.getNotes()
                : ""
        );
    }
}

