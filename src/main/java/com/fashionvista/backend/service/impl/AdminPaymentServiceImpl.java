package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminPaymentResponse;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.service.AdminPaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPaymentServiceImpl implements AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> getAllPayments(
        String search,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Pageable pageable
    ) {
        Specification<Payment> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search + "%";
            spec = spec.and((root, query, cb) ->
                cb.or(
                    cb.like(root.get("transactionId"), searchPattern),
                    cb.like(root.join("order").get("orderNumber"), searchPattern)
                )
            );
        }

        if (paymentMethod != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("paymentMethod"), paymentMethod));
        }

        if (paymentStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("paymentStatus"), paymentStatus));
        }

        Page<Payment> payments = paymentRepository.findAll(spec, pageable);
        return payments.map(this::toAdminPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy payment với ID: " + id));
        return toAdminPaymentResponse(payment);
    }

    @Override
    @Transactional
    public AdminPaymentResponse updatePaymentStatus(Long id, PaymentStatus paymentStatus) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy payment với ID: " + id));
        
        Order order = payment.getOrder();
        
        // Validation: Với đơn COD, chỉ cho phép đổi payment status thành PAID khi order status là DELIVERED
        if (order.getPaymentMethod() == PaymentMethod.COD
            && paymentStatus == PaymentStatus.PAID
            && order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Đơn hàng COD chỉ có thể được đánh dấu đã thanh toán khi trạng thái đơn hàng là 'Đã giao'.");
        }
        
        payment.setPaymentStatus(paymentStatus);
        Payment saved = paymentRepository.save(payment);
        
        // Đồng bộ paymentStatus với order
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
        
        return toAdminPaymentResponse(saved);
    }

    @Override
    @Transactional
    public int syncCodDeliveredPayments() {
        // Tìm tất cả đơn COD đã DELIVERED nhưng payment status vẫn PENDING
        List<Order> ordersToSync = orderRepository.findByPaymentMethodAndStatusAndPaymentStatus(
            PaymentMethod.COD,
            OrderStatus.DELIVERED,
            PaymentStatus.PENDING
        );

        int count = 0;
        for (Order order : ordersToSync) {
            Payment payment = paymentRepository.findByOrder(order).orElse(null);
            if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING) {
                // Cập nhật payment status
                payment.setPaymentStatus(PaymentStatus.PAID);
                paymentRepository.save(payment);
                
                // Cập nhật order payment status
                order.setPaymentStatus(PaymentStatus.PAID);
                orderRepository.save(order);
                
                count++;
            }
        }

        // Đồng bộ ngược lại: Nếu order.paymentStatus = PAID nhưng payment.paymentStatus = PENDING
        List<Order> ordersWithPaidStatus = orderRepository.findByPaymentMethodAndPaymentStatus(
            PaymentMethod.COD,
            PaymentStatus.PAID
        );
        
        for (Order order : ordersWithPaidStatus) {
            Payment payment = paymentRepository.findByOrder(order).orElse(null);
            if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setPaymentStatus(PaymentStatus.PAID);
                paymentRepository.save(payment);
                count++;
            }
        }

        return count;
    }

    private AdminPaymentResponse toAdminPaymentResponse(Payment payment) {
        Order order = payment.getOrder();
        return AdminPaymentResponse.builder()
            .id(payment.getId())
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .paymentMethod(payment.getPaymentMethod())
            .paymentStatus(payment.getPaymentStatus())
            .amount(payment.getAmount())
            .refundAmount(payment.getRefundAmount() != null ? payment.getRefundAmount() : java.math.BigDecimal.ZERO)
            .transactionId(payment.getTransactionId())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }
}

