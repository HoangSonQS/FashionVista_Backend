package com.fashionvista.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.service.LoyaltyService;
import com.fashionvista.backend.service.OrderService;
import com.fashionvista.backend.service.VnPayService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class VnPayController {

    private static final Logger log = LoggerFactory.getLogger(VnPayController.class);

    private final VnPayService vnPayService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final LoyaltyService loyaltyService;
    private final OrderService orderService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/return")
    @Transactional
    public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
        Map<String, Object> result = processPaymentResult(params, true);
        boolean success = (boolean) result.getOrDefault("success", false);
        String orderNumber = (String) result.get("orderNumber");

        String redirectUrl = frontendUrl;
        // Điều hướng về trang kết quả thanh toán chuyên biệt trên FE
        StringBuilder sb = new StringBuilder(frontendUrl)
            .append("/checkout/")
            .append(success ? "success" : "failed");
        if (orderNumber != null) {
            sb.append("?orderNumber=").append(orderNumber);
        }
        redirectUrl = sb.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirectUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @PostMapping("/ipn")
    @Transactional
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam Map<String, String> params) {
        Map<String, Object> result = processPaymentResult(params, false);
        Map<String, String> response = new HashMap<>();
        if ((boolean) result.getOrDefault("success", false)) {
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return ResponseEntity.ok(response);
        } else {
            response.put("RspCode", "97");
            response.put("Message", (String) result.getOrDefault("message", "Invalid signature"));
            return ResponseEntity.badRequest().body(response);
        }
    }

    private Map<String, Object> processPaymentResult(Map<String, String> params, boolean isReturnUrl) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        if (!vnPayService.validateSignature(params)) {
            response.put("message", "Chữ ký VNPay không hợp lệ.");
            response.put("status", 400);
            return response;
        }

        String orderNumber = params.get("vnp_TxnRef");
        if (orderNumber == null) {
            response.put("message", "Thiếu mã đơn hàng.");
            response.put("status", 400);
            return response;
        }

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElse(null);
        if (order == null) {
            response.put("message", "Không tìm thấy đơn hàng.");
            response.put("status", 404);
            return response;
        }

        Payment payment = paymentRepository.findByOrder(order)
            .orElse(null);
        if (payment == null) {
            response.put("message", "Không tìm thấy payment của đơn.");
            response.put("status", 404);
            return response;
        }

        String responseCode = params.get("vnp_ResponseCode");
        boolean success = "00".equals(responseCode);

        if (success) {
            order.setPaymentStatus(PaymentStatus.PAID);
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
            }
            payment.setPaymentStatus(PaymentStatus.PAID);
        } else {
            order.setPaymentStatus(PaymentStatus.FAILED);
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }

        payment.setTransactionId(params.get("vnp_TransactionNo"));
        try {
            payment.setPaymentGatewayResponse(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            log.warn("Không thể lưu response VNPay cho order {}", orderNumber, e);
        }

        orderRepository.save(order);
        paymentRepository.save(payment);

        // Nếu thanh toán VNPay thành công thì decrease stock và tích điểm
        if (success) {
            // Decrease stock cho order (vì chưa decrease khi checkout)
            orderService.decreaseStockForOrder(order);
            // Tích điểm loyalty
            loyaltyService.awardPointsForOrder(order);
        }

        response.put("success", success);
        response.put("message", success ? "Thanh toán VNPay thành công." : "Thanh toán VNPay thất bại.");
        response.put("status", success ? 200 : 400);
        response.put("orderNumber", orderNumber);
        response.put("responseCode", responseCode);
        response.put("isReturnUrl", isReturnUrl);
        return response;
    }
}


