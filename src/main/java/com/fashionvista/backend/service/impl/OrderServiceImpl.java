package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.CartResponse;
import com.fashionvista.backend.dto.CheckoutRequest;
import com.fashionvista.backend.dto.OrderItemResponse;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.entity.Cart;
import com.fashionvista.backend.entity.CartItem;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.ShippingMethod;
import com.fashionvista.backend.repository.CartRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.CartService;
import com.fashionvista.backend.service.EmailService;
import com.fashionvista.backend.service.OrderService;
import com.fashionvista.backend.service.UserContextService;
import com.fashionvista.backend.service.VnPayService;
import com.fashionvista.backend.service.VoucherService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    private final EmailService emailService;
    private final VoucherService voucherService;
    private final VnPayService vnPayService;

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

        // Tính phí ship trước khi áp dụng voucher (voucher hiện tại chỉ giảm trên subtotal)
        BigDecimal shippingFee = calculateShippingFee(cartResponse.getSubtotal(), request.getShippingMethod());
        order.setShippingFee(shippingFee);

        // Áp dụng voucher (nếu có)
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            discount = voucherService.validateAndCalculateDiscount(
                request.getVoucherCode(),
                order.getSubtotal()
            );
        }
        order.setDiscount(discount);
        order.setTotal(order.getSubtotal().add(shippingFee).subtract(discount));

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

        // Gửi email xác nhận đơn hàng
        try {
            emailService.sendOrderConfirmationEmail(saved);
        } catch (Exception e) {
            // Log lỗi nhưng không làm gián đoạn flow đặt hàng
            // Email có thể gửi lại sau
        }
        String paymentUrl = null;
        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            paymentUrl = vnPayService.createPaymentUrl(saved, resolveClientIp());
        }

        return toOrderResponse(saved, paymentUrl);
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

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getUser().getId().equals(userContextService.getCurrentUser().getId())) {
            throw new IllegalArgumentException("Bạn không có quyền hủy đơn hàng này.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new IllegalArgumentException("Đơn hàng đã được hủy hoặc hoàn tiền.");
        }

        if (!(order.getStatus() == OrderStatus.PENDING
            || order.getStatus() == OrderStatus.CONFIRMED
            || order.getStatus() == OrderStatus.PROCESSING)) {
            throw new IllegalArgumentException("Chỉ có thể hủy đơn khi đang chờ duyệt/xác nhận/xử lý.");
        }

        // Trả lại tồn kho vì khi checkout đã trừ kho
        restockItems(order);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        try {
            emailService.sendOrderStatusUpdateEmail(saved, oldStatus.name(), saved.getStatus().name());
        } catch (Exception e) {
            // không chặn hủy nếu gửi email thất bại
        }

        return toOrderResponse(saved);
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

        int affected = productVariantRepository.decreaseStockIfEnough(
            variant.getId(),
            item.getQuantity()
        );

        // Nếu không có dòng nào được update nghĩa là không còn đủ stock tại thời điểm checkout
        if (affected == 0) {
            throw new IllegalArgumentException("Sản phẩm " + variant.getSku() + " không đủ tồn kho.");
        }
    }

    private void restockItems(Order order) {
        order.getItems().forEach(orderItem -> {
            ProductVariant variant = productVariantRepository.findById(orderItem.getVariant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm."));
            variant.setStock(variant.getStock() + orderItem.getQuantity());
            try {
                productVariantRepository.save(variant);
            } catch (OptimisticLockingFailureException ex) {
                throw new IllegalStateException("Sản phẩm vừa được cập nhật. Vui lòng thử lại.", ex);
            }
        });
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
        return toOrderResponse(order, null);
    }

    private OrderResponse toOrderResponse(Order order, String paymentUrl) {
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

        String trackingUrl = null;
        if (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()) {
            // Generate tracking URL dựa trên shipping method
            if (order.getShippingMethod() != null) {
                switch (order.getShippingMethod()) {
                    case STANDARD, FAST, EXPRESS -> {
                        // Giả định dùng GHN
                        trackingUrl = "https://donhang.ghn.vn/?order_code=" + order.getTrackingNumber();
                    }
                    default -> {
                        // Fallback: tìm kiếm Google
                        trackingUrl = "https://www.google.com/search?q=" + order.getTrackingNumber();
                    }
                }
            }
        }

        return OrderResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .paymentMethod(order.getPaymentMethod())
            .paymentStatus(order.getPaymentStatus())
            .shippingMethod(order.getShippingMethod())
            .shippingAddress(order.getShippingAddress())
            .subtotal(order.getSubtotal())
            .shippingFee(order.getShippingFee())
            .discount(order.getDiscount())
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .items(itemResponses)
            .paymentUrl(paymentUrl)
            .trackingNumber(order.getTrackingNumber())
            .trackingUrl(trackingUrl)
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

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "127.0.0.1";
            }
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                return ip.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}

