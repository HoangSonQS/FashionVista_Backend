package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.CreateReturnRequestRequest;
import com.fashionvista.backend.dto.ReturnItemDto;
import com.fashionvista.backend.dto.ReturnRequestResponse;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.ReturnItem;
import com.fashionvista.backend.entity.ReturnRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.OrderItemRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ReturnRequestRepository;
import com.fashionvista.backend.service.ReturnService;
import com.fashionvista.backend.service.UserContextService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private static final int RETURN_WINDOW_DAYS = 7;

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserContextService userContextService;

    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(CreateReturnRequestRequest request) {
        User user = userContextService.getCurrentUser();

        Order order = orderRepository.findById(request.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Đơn hàng không thuộc về bạn.");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Chỉ đơn đã giao mới có thể yêu cầu đổi trả.");
        }

        if (returnRequestRepository.existsByOrderId(order.getId())) {
            throw new IllegalArgumentException("Đơn hàng đã có yêu cầu đổi trả.");
        }

        LocalDateTime deliveredAt = order.getUpdatedAt();
        long daysSinceDelivered = ChronoUnit.DAYS.between(deliveredAt, LocalDateTime.now());
        if (daysSinceDelivered > RETURN_WINDOW_DAYS) {
            throw new IllegalArgumentException("Đơn đã vượt quá thời hạn 7 ngày đổi trả.");
        }

        List<CreateReturnRequestRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn sản phẩm đổi trả.");
        }

        BigDecimal refundAmount = BigDecimal.ZERO;
        ReturnRequest returnRequest = ReturnRequest.builder()
            .order(order)
            .user(user)
            .status(ReturnStatus.REQUESTED)
            .reason(request.getReason())
            .note(request.getNote())
            .evidenceUrls(request.getEvidenceUrls())
            .build();

        for (CreateReturnRequestRequest.Item itemReq : items) {
            OrderItem orderItem = orderItemRepository.findById(itemReq.getOrderItemId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong đơn."));

            if (!orderItem.getOrder().getId().equals(order.getId())) {
                throw new IllegalArgumentException("Sản phẩm không thuộc đơn hàng.");
            }

            if (itemReq.getQuantity() <= 0 || itemReq.getQuantity() > orderItem.getQuantity()) {
                throw new IllegalArgumentException("Số lượng không hợp lệ.");
            }

            BigDecimal lineTotal = orderItem.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            refundAmount = refundAmount.add(lineTotal);

            ReturnItem returnItem = ReturnItem.builder()
                .returnRequest(returnRequest)
                .orderItem(orderItem)
                .quantity(itemReq.getQuantity())
                .unitPrice(orderItem.getPrice())
                .lineTotal(lineTotal)
                .build();
            returnRequest.getItems().add(returnItem);
        }

        returnRequest.setRefundAmount(refundAmount);
        order.setStatus(OrderStatus.RETURN_REQUESTED);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getMyReturns(Pageable pageable) {
        User user = userContextService.getCurrentUser();
        return returnRequestRepository.findByUser(user, pageable)
            .map(this::mapToResponse);
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


