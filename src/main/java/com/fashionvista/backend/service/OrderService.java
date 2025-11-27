package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.CheckoutRequest;
import com.fashionvista.backend.dto.OrderResponse;
import java.util.List;

public interface OrderService {

    OrderResponse checkout(CheckoutRequest request);

    List<OrderResponse> getMyOrders();

    OrderResponse getOrder(String orderNumber);
}

