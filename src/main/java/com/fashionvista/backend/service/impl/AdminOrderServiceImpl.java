package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminOrderListResponse;
import com.fashionvista.backend.dto.OrderItemResponse;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.UpdateOrderStatusRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.service.AdminOrderService;
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
    private final com.fashionvista.backend.service.UserContextService userContextService;

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
        com.fashionvista.backend.entity.PaymentStatus oldPaymentStatus = order.getPaymentStatus();

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

        // TODO: Gửi email thông báo khách hàng nếu request.getNotifyCustomer() == Boolean.TRUE

        return toOrderResponse(saved);
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
            .build();
    }
}

