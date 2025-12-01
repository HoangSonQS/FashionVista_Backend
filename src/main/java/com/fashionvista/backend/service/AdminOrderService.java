package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminOrderListResponse;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.UpdateOrderStatusRequest;
import com.fashionvista.backend.entity.OrderStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminOrderService {

    Page<AdminOrderListResponse> getAllOrders(
        String search,
        OrderStatus status,
        String paymentMethod,
        String startDate,
        String endDate,
        Pageable pageable
    );

    OrderResponse getOrderById(Long orderId);

    OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request);
}

