package com.fashionvista.backend.controller;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PrintExportController {

    private final OrderRepository orderRepository;

    @GetMapping("/{orderNumber}/invoice")
    public ResponseEntity<byte[]> invoice(@PathVariable String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        byte[] content = buildHtml(order, "Invoice");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + orderNumber + ".html")
            .contentType(MediaType.TEXT_HTML)
            .body(content);
    }

    @GetMapping("/{orderNumber}/delivery-note")
    public ResponseEntity<byte[]> deliveryNote(@PathVariable String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        byte[] content = buildHtml(order, "Delivery Note");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=delivery-" + orderNumber + ".html")
            .contentType(MediaType.TEXT_HTML)
            .body(content);
    }

    @GetMapping("/{orderNumber}/packing-slip")
    public ResponseEntity<byte[]> packingSlip(@PathVariable String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        byte[] content = buildHtml(order, "Packing Slip");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=packing-" + orderNumber + ".html")
            .contentType(MediaType.TEXT_HTML)
            .body(content);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOrders(
        @RequestParam(required = false) String status
    ) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("orderNumber,status,total,paymentStatus,shippingMethod,createdAt");
        orderRepository.findAll().forEach(order -> {
            if (status != null && !status.isBlank()) {
                String st = status.toUpperCase(Locale.ROOT);
                if (!order.getStatus().name().equalsIgnoreCase(st)) {
                    return;
                }
            }
            joiner.add(String.join(",",
                safe(order.getOrderNumber()),
                safe(order.getStatus().name()),
                order.getTotal() != null ? order.getTotal().toPlainString() : "",
                order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "",
                order.getShippingMethod() != null ? order.getShippingMethod().name() : "",
                order.getCreatedAt() != null ? order.getCreatedAt().toString() : ""
            ));
        });
        byte[] content = joiner.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    private byte[] buildHtml(Order order, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='utf-8'><title>")
            .append(title)
            .append("</title></head><body>");
        sb.append("<h1>").append(title).append("</h1>");
        sb.append("<p><strong>Order:</strong> ").append(order.getOrderNumber()).append("</p>");
        sb.append("<p><strong>Status:</strong> ").append(order.getStatus()).append("</p>");
        sb.append("<p><strong>Total:</strong> ").append(order.getTotal()).append("</p>");
        sb.append("<p><strong>Payment:</strong> ").append(order.getPaymentStatus()).append("</p>");
        sb.append("<p><strong>Shipping method:</strong> ").append(order.getShippingMethod()).append("</p>");
        sb.append("<p><strong>Tracking:</strong> ").append(order.getTrackingNumber() == null ? "—" : order.getTrackingNumber()).append("</p>");
        sb.append("<hr />");
        sb.append("<h3>Items</h3><ul>");
        order.getItems().forEach(item -> {
            sb.append("<li>")
                .append(item.getProductName())
                .append(" — Qty: ").append(item.getQuantity())
                .append(" — Price: ").append(item.getPrice())
                .append("</li>");
        });
        sb.append("</ul>");
        sb.append("</body></html>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String safe(String v) {
        return v == null ? "" : v.replace(",", " ");
    }
}

