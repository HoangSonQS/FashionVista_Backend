package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminOrderListResponse;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.UpdateOrderStatusRequest;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.service.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public Page<AdminOrderListResponse> getAllOrders(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) String paymentMethod,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return adminOrderService.getAllOrders(search, status, paymentMethod, startDate, endDate, pageable);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(@PathVariable Long orderId) {
        return adminOrderService.getOrderById(orderId);
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse updateOrderStatus(
        @PathVariable Long orderId,
        @RequestBody @Valid UpdateOrderStatusRequest request
    ) {
        return adminOrderService.updateOrderStatus(orderId, request);
    }
}

