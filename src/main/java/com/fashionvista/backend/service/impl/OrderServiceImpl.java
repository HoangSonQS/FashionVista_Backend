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
import com.fashionvista.backend.entity.ShippingMethod;
import com.fashionvista.backend.repository.CartRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.CartService;
import com.fashionvista.backend.service.OrderService;
import com.fashionvista.backend.service.UserContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
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
    private final ObjectMapper objectMapper;

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
            .shippingMethod(request.getShippingMethod())
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
        BigDecimal shippingFee = calculateShippingFee(cartResponse.getSubtotal(), request.getShippingMethod());
        order.setShippingFee(shippingFee);
        order.setTotal(order.getSubtotal().add(shippingFee));
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
                .productImage(item.getProductImage())
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
            .shippingMethod(order.getShippingMethod())
            .subtotal(order.getSubtotal())
            .shippingFee(order.getShippingFee())
            .discount(order.getDiscount())
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .items(itemResponses)
            .build();
    }

    private BigDecimal calculateShippingFee(BigDecimal subtotal, ShippingMethod method) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Free shipping cho đơn từ 1,000,000đ
        BigDecimal threshold = BigDecimal.valueOf(1_000_000);
        if (subtotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal base = BigDecimal.valueOf(30_000);
        return switch (method) {
            case FAST -> base.add(BigDecimal.valueOf(10_000));
            case EXPRESS -> base.add(BigDecimal.valueOf(20_000));
            case STANDARD -> base;
        };
    }

    private String generateOrderNumber() {
        // Sử dụng ngày + đoạn UUID ngắn để tránh trùng lặp khi restart service
        String datePart = java.time.LocalDate.now().toString().replace("-", ""); // yyyyMMdd
        String randomPart = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    private String buildShippingSnapshot(CheckoutRequest request) {
        var snapshot = new java.util.LinkedHashMap<String, Object>();
        snapshot.put("fullName", request.getFullName());
        snapshot.put("phone", request.getPhone());
        snapshot.put("address", request.getAddress());
        snapshot.put("ward", request.getWard());
        snapshot.put("district", request.getDistrict());
        snapshot.put("city", request.getCity());
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            snapshot.put("notes", request.getNotes());
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể tạo thông tin địa chỉ giao hàng.", e);
        }
    }
}

