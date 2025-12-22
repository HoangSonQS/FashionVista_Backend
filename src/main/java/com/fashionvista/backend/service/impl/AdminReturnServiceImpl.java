package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.ReturnItemDto;
import com.fashionvista.backend.dto.ReturnRequestResponse;
import com.fashionvista.backend.dto.UpdateReturnStatusRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.ReturnRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.repository.ReturnRequestRepository;
import com.fashionvista.backend.service.AdminReturnService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
public class AdminReturnServiceImpl implements AdminReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getAll(ReturnStatus status, String search, Pageable pageable) {
        String keyword = search != null ? search.trim() : null;
        if (keyword != null && !keyword.isBlank()) {
            return returnRequestRepository.searchByStatusAndKeyword(status, keyword, pageable)
                .map(this::mapToResponse);
        }

        if (status != null) {
            return returnRequestRepository.findByStatus(status, pageable).map(this::mapToResponse);
        }
        return returnRequestRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getByOrder(Long orderId) {
        ReturnRequest request = returnRequestRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Đơn hàng chưa có yêu cầu đổi trả."));
        return mapToResponse(request);
    }

    @Override
    @Transactional
    public ReturnRequestResponse updateStatus(Long id, UpdateReturnStatusRequest request) {
        ReturnRequest returnRequest = returnRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu đổi trả."));

        Order order = returnRequest.getOrder();
        OrderStatus oldStatus = order.getStatus();
        ReturnStatus targetStatus = request.getStatus();
        if (targetStatus == ReturnStatus.APPROVED) {
            returnRequest.setStatus(ReturnStatus.APPROVED);
            returnRequest.setApprovedAt(LocalDateTime.now());
            returnRequest.setRefundMethod(request.getRefundMethod());
            if (request.getRefundAmount() != null) {
                returnRequest.setRefundAmount(request.getRefundAmount());
            }
            order.setStatus(OrderStatus.RETURN_APPROVED);
            if (isOnlinePayment(order.getPaymentMethod())) {
                order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            }
            restockReturnItemsIfNeeded(returnRequest, oldStatus, order.getStatus());
        } else if (targetStatus == ReturnStatus.REJECTED) {
            returnRequest.setStatus(ReturnStatus.REJECTED);
            returnRequest.setRejectedAt(LocalDateTime.now());
            order.setStatus(OrderStatus.DELIVERED);
        } else if (targetStatus == ReturnStatus.REFUND_PENDING) {
            returnRequest.setStatus(ReturnStatus.REFUND_PENDING);
            if (request.getRefundAmount() != null) {
                returnRequest.setRefundAmount(request.getRefundAmount());
            }
            if (isOnlinePayment(order.getPaymentMethod())) {
                order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            }
        } else if (targetStatus == ReturnStatus.REFUNDED) {
            returnRequest.setStatus(ReturnStatus.REFUNDED);
            returnRequest.setRefundedAt(LocalDateTime.now());
            if (request.getRefundAmount() != null) {
                returnRequest.setRefundAmount(request.getRefundAmount());
            }
            order.setStatus(OrderStatus.REFUNDED);
            if (isOnlinePayment(order.getPaymentMethod())) {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            restockReturnItemsIfNeeded(returnRequest, oldStatus, order.getStatus());
        } else {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }

        if (request.getAdminNote() != null) {
            returnRequest.setAdminNote(request.getAdminNote());
        }
        if (request.getRefundMethod() != null) {
            returnRequest.setRefundMethod(request.getRefundMethod());
        }

        orderRepository.save(order);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        return mapToResponse(saved);
    }

    private boolean isOnlinePayment(PaymentMethod paymentMethod) {
        return paymentMethod != PaymentMethod.COD;
    }

    /**
     * Trả tồn kho khi đơn đổi trả được chấp nhận/làm hoàn tiền,
     * tránh cộng trùng bằng cách chỉ restock khi chuyển lần đầu vào trạng thái RETURN_APPROVED/REFUNDED.
     */
    private void restockReturnItemsIfNeeded(ReturnRequest returnRequest, OrderStatus oldOrderStatus, OrderStatus newOrderStatus) {
        boolean alreadyRestocked = oldOrderStatus == OrderStatus.RETURN_APPROVED || oldOrderStatus == OrderStatus.REFUNDED;
        boolean shouldRestock = newOrderStatus == OrderStatus.RETURN_APPROVED || newOrderStatus == OrderStatus.REFUNDED;
        if (alreadyRestocked || !shouldRestock) {
            return;
        }

        returnRequest.getItems().forEach(item -> {
            if (item.getOrderItem().getVariant() == null) {
                return; // không có biến thể thì bỏ qua
            }
            var variant = productVariantRepository.findById(item.getOrderItem().getVariant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm."));
            variant.setStock(variant.getStock() + item.getQuantity());
            productVariantRepository.save(variant);
        });
    }

    private ReturnRequestResponse mapToResponse(ReturnRequest request) {
        return ReturnRequestResponse.builder()
            .id(request.getId())
            .orderId(request.getOrder().getId())
            .orderNumber(request.getOrder().getOrderNumber())
            .status(request.getStatus())
            .reason(request.getReason())
            .note(request.getNote())
            .evidenceUrls(request.getEvidenceUrls())
            .refundAmount(request.getRefundAmount())
            .refundMethod(request.getRefundMethod())
            .adminNote(request.getAdminNote())
            .items(request.getItems().stream()
                .map(item -> ReturnItemDto.builder()
                    .orderItemId(item.getOrderItem().getId())
                    .productName(item.getOrderItem().getProductName())
                    .productImage(item.getOrderItem().getProductImage())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .lineTotal(item.getLineTotal())
                    .build())
                .toList())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .approvedAt(request.getApprovedAt())
            .rejectedAt(request.getRejectedAt())
            .refundedAt(request.getRefundedAt())
            .build();
    }
}


