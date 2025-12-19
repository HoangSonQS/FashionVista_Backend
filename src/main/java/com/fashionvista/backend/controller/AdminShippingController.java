package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.ShippingCreateRequest;
import com.fashionvista.backend.dto.ShippingWebhookPayload;
import com.fashionvista.backend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/shipping")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShippingController {

    private final ShippingService shippingService;

    @PostMapping("/{orderNumber}/create")
    public OrderResponse createShipping(
        @PathVariable String orderNumber,
        @RequestBody ShippingCreateRequest request
    ) {
        return shippingService.createShipping(orderNumber, request);
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancelShipping(
        @PathVariable String orderNumber,
        @RequestBody(required = false) java.util.Map<String, String> payload
    ) {
        String note = payload != null ? payload.getOrDefault("note", "") : "";
        return shippingService.cancelShipping(orderNumber, note);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody ShippingWebhookPayload payload) {
        shippingService.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }
}

