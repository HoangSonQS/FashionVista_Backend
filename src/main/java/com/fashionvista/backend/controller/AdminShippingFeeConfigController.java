package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ShippingFeeConfigCreateRequest;
import com.fashionvista.backend.dto.ShippingFeeConfigResponse;
import com.fashionvista.backend.dto.ShippingFeeConfigUpdateRequest;
import com.fashionvista.backend.service.ShippingFeeConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ShippingFeeConfigResponse create(@Valid @RequestBody ShippingFeeConfigCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ShippingFeeConfigResponse update(@PathVariable Long id, @Valid @RequestBody ShippingFeeConfigUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
