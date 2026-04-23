package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ShippingFeeConfigResponse;
import com.fashionvista.backend.service.ShippingFeeConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping-fee-configs")
@RequiredArgsConstructor
public class ShippingFeeConfigController {

    private final ShippingFeeConfigService service;

    @GetMapping
    public ResponseEntity<List<ShippingFeeConfigResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/by-method")
    public ResponseEntity<ShippingFeeConfigResponse> getByMethod(@RequestParam String method) {
        return ResponseEntity.ok(service.getByMethod(method));
    }
}



