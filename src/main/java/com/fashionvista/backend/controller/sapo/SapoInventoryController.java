package com.fashionvista.backend.controller.sapo;

import com.fashionvista.backend.dto.sapo.SapoInventoryDto;
import com.fashionvista.backend.dto.sapo.SapoInventoryUpdateRequest;
import com.fashionvista.backend.dto.sapo.SapoPageResponse;
import com.fashionvista.backend.dto.sapo.SapoResponse;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.exception.SapoNotFoundException;
import com.fashionvista.backend.repository.ProductVariantRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sapo/v1/inventory")
@RequiredArgsConstructor
@Validated
public class SapoInventoryController {

    private final ProductVariantRepository productVariantRepository;

    @GetMapping
    public SapoPageResponse<SapoInventoryDto> getInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") @Max(500) int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter) {

        Specification<ProductVariant> spec = (root, query, cb) ->
            updatedAfter != null
                ? cb.greaterThan(root.get("updatedAt"), updatedAfter)
                : cb.conjunction();

        Page<SapoInventoryDto> result = productVariantRepository
            .findAll(spec, PageRequest.of(page, size))
            .map(this::toDto);

        return SapoPageResponse.of(result);
    }

    @PutMapping("/{sku}")
    @Transactional
    public SapoResponse<SapoInventoryDto> updateStock(
            @PathVariable String sku,
            @RequestBody @Valid SapoInventoryUpdateRequest request) {

        ProductVariant variant = productVariantRepository.findBySku(sku)
            .orElseThrow(() -> new SapoNotFoundException("Variant not found: " + sku));

        variant.setStock(request.getStock());
        ProductVariant saved = productVariantRepository.save(variant);
        return SapoResponse.ok(toDto(saved));
    }

    private SapoInventoryDto toDto(ProductVariant v) {
        return SapoInventoryDto.builder()
            .sku(v.getSku())
            .productName(v.getProduct().getName())
            .size(v.getSize())
            .color(v.getColor())
            .stock(v.getStock())
            .updatedAt(v.getUpdatedAt())
            .build();
    }
}
