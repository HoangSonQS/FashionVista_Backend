package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminProductVariantCreateRequest;
import com.fashionvista.backend.dto.AdminProductVariantResponse;
import com.fashionvista.backend.dto.AdminProductVariantUpdateRequest;
import com.fashionvista.backend.service.AdminProductVariantService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/product-variants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductVariantController {

    private final AdminProductVariantService adminProductVariantService;

    @GetMapping
    public Page<AdminProductVariantResponse> getAll(
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false, name = "variantSize") String variantSize, // Renamed to avoid conflict with pageable 'size'
        @RequestParam(required = false) String color,
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) Integer minStock,
        @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        return adminProductVariantService.getAll(productId, search, variantSize, color, active, minStock, pageable);
    }

    @GetMapping("/{id}")
    public AdminProductVariantResponse getById(@PathVariable Long id) {
        return adminProductVariantService.getById(id);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AdminProductVariantResponse create(@RequestBody @Valid AdminProductVariantCreateRequest request) {
        return adminProductVariantService.create(request);
    }

    @PutMapping("/{id}")
    public AdminProductVariantResponse update(
        @PathVariable Long id,
        @RequestBody @Valid AdminProductVariantUpdateRequest request
    ) {
        return adminProductVariantService.update(id, request);
    }

    @PatchMapping("/{id}/stock")
    public AdminProductVariantResponse updateStock(
        @PathVariable Long id,
        @RequestParam Integer stock
    ) {
        return adminProductVariantService.updateStock(id, stock);
    }

    @PatchMapping("/{id}/price")
    public AdminProductVariantResponse updatePrice(
        @PathVariable Long id,
        @RequestParam BigDecimal price
    ) {
        return adminProductVariantService.updatePrice(id, price);
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adminProductVariantService.delete(id);
    }
}

