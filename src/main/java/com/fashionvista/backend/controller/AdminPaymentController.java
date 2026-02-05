package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminPaymentResponse;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.service.AdminPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping
    public Page<AdminPaymentResponse> getAllPayments(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) PaymentMethod paymentMethod,
        @RequestParam(required = false) PaymentStatus paymentStatus,
        @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        return adminPaymentService.getAllPayments(search, paymentMethod, paymentStatus, pageable);
    }

    @GetMapping("/{id}")
    public AdminPaymentResponse getPaymentById(@PathVariable Long id) {
        return adminPaymentService.getPaymentById(id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/status")
    public AdminPaymentResponse updatePaymentStatus(
        @PathVariable Long id,
        @org.springframework.web.bind.annotation.RequestBody com.fashionvista.backend.dto.PaymentStatusUpdateRequest request
    ) {
        return adminPaymentService.updatePaymentStatus(id, request.getPaymentStatus());
    }

    @org.springframework.web.bind.annotation.PostMapping("/sync-cod-delivered")
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> syncCodDeliveredPayments() {
        int count = adminPaymentService.syncCodDeliveredPayments();
        return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
            "message", "Đã đồng bộ " + count + " đơn hàng COD đã giao.",
            "updatedCount", count
        ));
    }
}

