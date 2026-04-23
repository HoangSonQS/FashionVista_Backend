package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.VoucherDiscountResult;
import com.fashionvista.backend.dto.VoucherValidationResponse;
import com.fashionvista.backend.service.VoucherService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    /**
     * API validate voucher cho FE checkout:
     * - Input: code + subtotal hiện tại
     * - Output: discount + finalTotal và message
     */
    @GetMapping("/validate")
    public ResponseEntity<VoucherValidationResponse> validateVoucher(
        @RequestParam String code,
        @RequestParam BigDecimal subtotal
    ) {
        try {
            VoucherDiscountResult result = voucherService.validateAndCalculateDiscount(code, subtotal);
            BigDecimal discount = result.getDiscount();
            BigDecimal finalTotal = subtotal.subtract(discount).max(BigDecimal.ZERO);
            VoucherValidationResponse response = VoucherValidationResponse.builder()
                .valid(true)
                .message("Áp dụng voucher thành công. (Updated)")
                .discount(discount)
                .subtotal(subtotal)
                .finalTotal(finalTotal)
                .freeShipping(result.isFreeShipping())
                .build();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            VoucherValidationResponse response = VoucherValidationResponse.builder()
                .valid(false)
                .message(ex.getMessage())
                .discount(BigDecimal.ZERO)
                .subtotal(subtotal)
                .finalTotal(subtotal)
                .build();
            return ResponseEntity.badRequest().body(response);
        }
    }
}


