package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminOrderListResponse;
import com.fashionvista.backend.dto.BulkUpdateOrderStatusRequest;
import com.fashionvista.backend.dto.OrderHistoryItemResponse;
import com.fashionvista.backend.dto.OrderItemResponse;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.UpdateOrderStatusRequest;
import com.fashionvista.backend.dto.UpdateTrackingNumberRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.OrderHistory;
import com.fashionvista.backend.repository.OrderHistoryRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.service.AdminOrderService;
import com.fashionvista.backend.service.EmailService;
import com.fashionvista.backend.service.LoyaltyService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final com.fashionvista.backend.service.UserContextService userContextService;
    private final EmailService emailService;
    private final LoyaltyService loyaltyService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderListResponse> getAllOrders(
        String search,
        OrderStatus status,
        String paymentMethod,
        String startDate,
        String endDate,
        Pageable pageable
    ) {
        Specification<Order> spec = buildSpecification(search, status, paymentMethod, startDate, endDate);
        return orderRepository.findAll(spec, pageable)
            .map(this::toAdminOrderListResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        return toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        OrderStatus oldStatus = order.getStatus();
        PaymentStatus oldPaymentStatus = order.getPaymentStatus();

        order.setStatus(request.getStatus());
        if (request.getPaymentStatus() != null && request.getPaymentStatus() != order.getPaymentStatus()) {
            order.setPaymentStatus(request.getPaymentStatus());
        }

        // Ghi log nội bộ tự động
        StringBuilder log = new StringBuilder();
        log.append("[").append(LocalDateTime.now()).append("] ");
        try {
            var admin = userContextService.getCurrentUser();
            log.append("Admin ").append(admin.getEmail() != null ? admin.getEmail() : admin.getId());
        } catch (Exception e) {
            log.append("Admin");
        }
        log.append(" cập nhật đơn hàng. ");
        if (oldStatus != order.getStatus()) {
            log.append("Trạng thái: ").append(oldStatus).append(" → ").append(order.getStatus()).append(". ");
        }
        if (oldPaymentStatus != order.getPaymentStatus()) {
            log.append("Thanh toán: ").append(oldPaymentStatus).append(" → ").append(order.getPaymentStatus()).append(". ");
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            log.append("Ghi chú thêm: ").append(request.getNotes());
        }

        String existingNotes = order.getNotes() != null ? order.getNotes() : "";
        if (!existingNotes.isBlank()) {
            existingNotes = existingNotes + "\n";
        }
        order.setNotes(existingNotes + log);

        Order saved = orderRepository.save(order);
        recordHistory(saved, "status", oldStatus.name(), saved.getStatus().name(), request.getNotes());
        if (oldPaymentStatus != saved.getPaymentStatus()) {
            recordHistory(saved, "paymentStatus", oldPaymentStatus.name(), saved.getPaymentStatus().name(), request.getNotes());
        }

        // Nếu là đơn COD và lần đầu chuyển sang DELIVERED + đã thanh toán, thì tích điểm loyalty
        if (saved.getPaymentMethod() == PaymentMethod.COD
            && saved.getStatus() == OrderStatus.DELIVERED
            && oldStatus != OrderStatus.DELIVERED) {
            loyaltyService.awardPointsForOrder(saved);
        }

        // Gửi email thông báo khách hàng nếu request.getNotifyCustomer() == Boolean.TRUE
        if (Boolean.TRUE.equals(request.getNotifyCustomer())) {
            try {
                // Sử dụng trạng thái mới từ order sau khi đã cập nhật
                emailService.sendOrderStatusUpdateEmail(saved, oldStatus.name(), saved.getStatus().name());
            } catch (Exception e) {
                // Log lỗi nhưng không làm gián đoạn flow
            }
        }

        return toOrderResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse updateTrackingNumber(Long orderId, UpdateTrackingNumberRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        // Chỉ cho phép cập nhật tracking khi đơn đã chuyển sang SHIPPING hoặc DELIVERED
        if (order.getStatus() != OrderStatus.SHIPPING && order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Chỉ có thể cập nhật mã vận đơn khi đơn hàng đang giao hoặc đã giao.");
        }

        order.setTrackingNumber(request.getTrackingNumber());
        Order saved = orderRepository.save(order);
        recordHistory(saved, "trackingNumber", null, request.getTrackingNumber(), request.getNotifyCustomer() != null && request.getNotifyCustomer() ? "Notify customer" : null);

        // Ghi log
        StringBuilder log = new StringBuilder();
        log.append("[").append(LocalDateTime.now()).append("] ");
        try {
            var admin = userContextService.getCurrentUser();
            log.append("Admin ").append(admin.getEmail() != null ? admin.getEmail() : admin.getId());
        } catch (Exception e) {
            log.append("Admin");
        }
        log.append(" cập nhật mã vận đơn: ").append(request.getTrackingNumber());

        String existingNotes = order.getNotes() != null ? order.getNotes() : "";
        if (!existingNotes.isBlank()) {
            existingNotes = existingNotes + "\n";
        }
        order.setNotes(existingNotes + log);
        saved = orderRepository.save(order);

        // Gửi email thông báo khách hàng nếu request.getNotifyCustomer() == Boolean.TRUE
        if (Boolean.TRUE.equals(request.getNotifyCustomer())) {
            try {
                emailService.sendOrderStatusUpdateEmail(saved, saved.getStatus().name(), saved.getStatus().name());
            } catch (Exception e) {
                // Log lỗi nhưng không làm gián đoạn flow
            }
        }

        return toOrderResponse(saved);
    }

    @Override
    @Transactional
    public void bulkUpdateStatus(BulkUpdateOrderStatusRequest request) {
        if (request.getOrderIds() == null || request.getOrderIds().isEmpty()) {
            return;
        }
        List<Order> orders = orderRepository.findAllById(request.getOrderIds());
        LocalDateTime now = LocalDateTime.now();
        for (Order order : orders) {
            OrderStatus oldStatus = order.getStatus();
            PaymentStatus oldPaymentStatus = order.getPaymentStatus();

            order.setStatus(request.getStatus());
            if (request.getPaymentStatus() != null && request.getPaymentStatus() != order.getPaymentStatus()) {
                order.setPaymentStatus(request.getPaymentStatus());
            }

            // Append log to notes (reuse existing pattern)
            StringBuilder log = new StringBuilder();
            log.append("[").append(now).append("] ");
            try {
                var admin = userContextService.getCurrentUser();
                log.append("Admin ").append(admin.getEmail() != null ? admin.getEmail() : admin.getId());
            } catch (Exception e) {
                log.append("Admin");
            }
            log.append(" cập nhật hàng loạt. ");
            if (oldStatus != order.getStatus()) {
                log.append("Trạng thái: ").append(oldStatus).append(" → ").append(order.getStatus()).append(". ");
            }
            if (oldPaymentStatus != order.getPaymentStatus()) {
                log.append("Thanh toán: ").append(oldPaymentStatus).append(" → ").append(order.getPaymentStatus()).append(". ");
            }
            if (request.getNotes() != null && !request.getNotes().isBlank()) {
                log.append("Ghi chú thêm: ").append(request.getNotes());
            }

            String existingNotes = order.getNotes() != null ? order.getNotes() : "";
            if (!existingNotes.isBlank()) {
                existingNotes = existingNotes + "\n";
            }
            order.setNotes(existingNotes + log);

            order.setUpdatedAt(now);
            recordHistory(order, "status", oldStatus.name(), order.getStatus().name(), request.getNotes());
            if (oldPaymentStatus != order.getPaymentStatus()) {
                recordHistory(order, "paymentStatus", oldPaymentStatus.name(), order.getPaymentStatus().name(), request.getNotes());
            }
        }
        orderRepository.saveAll(orders);
    }

    private Specification<Order> buildSpecification(
        String search,
        OrderStatus status,
        String paymentMethod,
        String startDate,
        String endDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Predicate orderNumberPredicate = cb.like(cb.lower(root.get("orderNumber")), searchPattern);
                Predicate customerNamePredicate = cb.like(cb.lower(root.get("user").get("fullName")), searchPattern);
                Predicate customerEmailPredicate = cb.like(cb.lower(root.get("user").get("email")), searchPattern);
                predicates.add(cb.or(orderNumberPredicate, customerNamePredicate, customerEmailPredicate));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (paymentMethod != null && !paymentMethod.isBlank()) {
                try {
                    PaymentMethod method = PaymentMethod.valueOf(paymentMethod.toUpperCase());
                    predicates.add(cb.equal(root.get("paymentMethod"), method));
                } catch (IllegalArgumentException e) {
                    // Ignore invalid payment method
                }
            }

            if (startDate != null && !startDate.isBlank()) {
                try {
                    LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start.atStartOfDay()));
                } catch (Exception e) {
                    // Ignore invalid date format
                }
            }

            if (endDate != null && !endDate.isBlank()) {
                try {
                    LocalDate end = LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE);
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end.atTime(23, 59, 59)));
                } catch (Exception e) {
                    // Ignore invalid date format
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdminOrderListResponse toAdminOrderListResponse(Order order) {
        return AdminOrderListResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .customerName(order.getUser().getFullName())
            .customerEmail(order.getUser().getEmail())
            .customerPhone(order.getUser().getPhoneNumber())
            .status(order.getStatus())
            .paymentMethod(order.getPaymentMethod())
            .paymentStatus(order.getPaymentStatus())
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .itemCount(order.getItems().size())
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

        List<OrderHistoryItemResponse> historyDtos = orderHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
            .map(h -> OrderHistoryItemResponse.builder()
                .field(h.getField())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .actor(h.getActor())
                .note(h.getNote())
                .createdAt(h.getCreatedAt())
                .build())
            .toList();

        String trackingUrl = null;
        if (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()) {
            // Generate tracking URL dựa trên shipping method
            // GHN: https://donhang.ghn.vn/?order_code={trackingNumber}
            // GHTK: https://khachhang.giaohangtietkiem.vn/don-hang/{trackingNumber}
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
            .billingAddress(order.getBillingAddress())
            .subtotal(order.getSubtotal())
            .shippingFee(order.getShippingFee())
            .discount(order.getDiscount())
            .voucherDiscount(order.getDiscount())
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .items(itemResponses)
            .trackingNumber(order.getTrackingNumber())
            .trackingUrl(trackingUrl)
            .customerEmail(order.getUser().getEmail())
            .customerPhone(order.getUser().getPhoneNumber())
            .customerGroup(order.getUser().getTier() != null ? order.getUser().getTier().name() : null)
            .transactionId(order.getPayment() != null ? order.getPayment().getTransactionId() : null)
            .history(historyDtos)
            .build();
    }

    private void recordHistory(Order order, String field, String oldValue, String newValue, String note) {
        try {
            String actor;
            try {
                var admin = userContextService.getCurrentUser();
                actor = admin.getEmail() != null ? admin.getEmail() : String.valueOf(admin.getId());
            } catch (Exception e) {
                actor = "Admin";
            }
            OrderHistory history = OrderHistory.builder()
                .order(order)
                .field(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .actor(actor)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build();
            orderHistoryRepository.save(history);
        } catch (Exception e) {
            // ignore history failures
        }
    }
}

