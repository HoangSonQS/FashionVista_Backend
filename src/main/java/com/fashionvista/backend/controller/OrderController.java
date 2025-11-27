package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.CheckoutRequest;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public OrderResponse checkout(@RequestBody @Valid CheckoutRequest request) {
        return orderService.checkout(request);
    }

    @GetMapping
    public List<OrderResponse> getOrders() {
        return orderService.getMyOrders();
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse getOrder(@PathVariable String orderNumber) {
        return orderService.getOrder(orderNumber);
    }
}

