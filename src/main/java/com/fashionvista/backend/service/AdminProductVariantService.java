package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminProductVariantCreateRequest;
import com.fashionvista.backend.dto.AdminProductVariantResponse;
import com.fashionvista.backend.dto.AdminProductVariantUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminProductVariantService {
    Page<AdminProductVariantResponse> getAll(
        Long productId,
        String search,
        String size,
        String color,
        Boolean active,
        Integer minStock,
        Pageable pageable
    );

    AdminProductVariantResponse getById(Long id);

    AdminProductVariantResponse create(AdminProductVariantCreateRequest request);

    AdminProductVariantResponse update(Long id, AdminProductVariantUpdateRequest request);

    void delete(Long id);

    AdminProductVariantResponse updateStock(Long id, Integer stock);

    AdminProductVariantResponse updatePrice(Long id, java.math.BigDecimal price);
}

