package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.CheckoutRequest;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.entity.Order;
import java.util.List;

public interface OrderService {

    OrderResponse checkout(CheckoutRequest request);

    List<OrderResponse> getMyOrders();

    OrderResponse getOrder(String orderNumber);

    OrderResponse cancelMyOrder(String orderNumber);

    /**
     * Decrease stock cho các items trong order (dùng khi VNPay payment success)
     */
    void decreaseStockForOrder(Order order);
}

