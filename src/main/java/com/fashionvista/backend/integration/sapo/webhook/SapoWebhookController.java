package com.fashionvista.backend.integration.sapo.webhook;

import com.fashionvista.backend.dto.UpdateOrderStatusRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.dto.SapoWebhookInventoryPayload;
import com.fashionvista.backend.integration.sapo.dto.SapoWebhookOrderPayload;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.AdminOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/sapo")
@RequiredArgsConstructor
public class SapoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SapoWebhookController.class);

    private final SapoHmacVerifier hmacVerifier;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final AdminOrderService adminOrderService;
    private final ObjectMapper objectMapper;

    @PostMapping("/inventory-update")
    @Transactional
    public ResponseEntity<Void> handleInventoryUpdate(
            HttpServletRequest request,
            @RequestHeader(value = "X-Sapo-Hmac-SHA256", required = false) String signature) throws IOException {

        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!hmacVerifier.isValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // TEMPORARY DIAGNOSTIC LOGGING - remove after confirming real Sapo payload schema
        log.info("Sapo inventory webhook raw body: {}", new String(rawBody, java.nio.charset.StandardCharsets.UTF_8));

        SapoWebhookInventoryPayload payload = objectMapper.readValue(rawBody, SapoWebhookInventoryPayload.class);

        ProductVariant variant = resolveVariant(payload);
        if (variant == null) {
            log.info("Sapo inventory webhook: no local variant found for variantId={} sku={}",
                    payload.getVariantId(), payload.getSku());
            return ResponseEntity.ok().build();
        }

        if (payload.getInventoryQuantity() == null) {
            log.warn("Sapo inventory webhook: missing inventoryQuantity for variantId={} sku={}",
                    payload.getVariantId(), payload.getSku());
            return ResponseEntity.ok().build();
        }

        variant.setStock(payload.getInventoryQuantity());
        productVariantRepository.save(variant);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/order-fulfilled")
    @Transactional
    public ResponseEntity<Void> handleOrderFulfilled(
            HttpServletRequest request,
            @RequestHeader(value = "X-Sapo-Hmac-SHA256", required = false) String signature) throws IOException {
        return handleOrderStatusWebhook(request, signature, OrderStatus.DELIVERED,
                "Sapo xác nhận đơn hàng đã giao (orders/fulfilled).");
    }

    @PostMapping("/order-cancelled")
    @Transactional
    public ResponseEntity<Void> handleOrderCancelled(
            HttpServletRequest request,
            @RequestHeader(value = "X-Sapo-Hmac-SHA256", required = false) String signature) throws IOException {
        return handleOrderStatusWebhook(request, signature, OrderStatus.CANCELLED,
                "Sapo xác nhận đơn hàng đã hủy (orders/cancelled).");
    }

    private ResponseEntity<Void> handleOrderStatusWebhook(
            HttpServletRequest request,
            String signature,
            OrderStatus newStatus,
            String note) throws IOException {

        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!hmacVerifier.isValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SapoWebhookOrderPayload payload = objectMapper.readValue(rawBody, SapoWebhookOrderPayload.class);

        Optional<Order> order = orderRepository.findBySapoOrderId(payload.getId());
        if (order.isEmpty()) {
            log.info("Sapo order webhook: no local order found for sapoOrderId={}", payload.getId());
            return ResponseEntity.ok().build();
        }

        adminOrderService.updateOrderStatus(order.get().getId(),
                new UpdateOrderStatusRequest(newStatus, null, false, note));
        return ResponseEntity.ok().build();
    }

    private ProductVariant resolveVariant(SapoWebhookInventoryPayload payload) {
        if (payload.getVariantId() != null) {
            Optional<ProductVariant> bySapoId = productVariantRepository
                    .findBySapoVariantId(String.valueOf(payload.getVariantId()));
            if (bySapoId.isPresent()) {
                return bySapoId.get();
            }
        }
        if (payload.getSku() != null) {
            return productVariantRepository.findBySku(payload.getSku()).orElse(null);
        }
        return null;
    }
}
