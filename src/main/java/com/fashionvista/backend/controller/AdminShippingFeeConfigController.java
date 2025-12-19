package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ShippingFeeConfigResponse;
import com.fashionvista.backend.dto.ShippingFeeConfigUpdateRequest;
import com.fashionvista.backend.service.ShippingFeeConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/shipping-fee-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShippingFeeConfigController {

    private final ShippingFeeConfigService service;

    @GetMapping
    public ResponseEntity<List<ShippingFeeConfigResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShippingFeeConfigResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody ShippingFeeConfigUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }
}

