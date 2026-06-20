package com.fashionvista.backend.controller.sapo;

import com.fashionvista.backend.dto.sapo.SapoPageResponse;
import com.fashionvista.backend.dto.sapo.SapoProductDto;
import com.fashionvista.backend.dto.sapo.SapoResponse;
import com.fashionvista.backend.dto.sapo.SapoVariantDto;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.exception.SapoNotFoundException;
import com.fashionvista.backend.repository.ProductRepository;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sapo/v1/products")
@RequiredArgsConstructor
@Validated
public class SapoProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public SapoPageResponse<SapoProductDto> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") @Max(200) int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter) {

        Specification<Product> spec = (root, query, cb) ->
            updatedAfter != null
                ? cb.greaterThan(root.get("updatedAt"), updatedAfter)
                : cb.conjunction();

        Page<SapoProductDto> result = productRepository
            .findAll(spec, PageRequest.of(page, size, Sort.by("updatedAt").descending()))
            .map(this::toDto);

        return SapoPageResponse.of(result);
    }

    @GetMapping("/{sku}")
    public SapoResponse<SapoProductDto> getProductBySku(@PathVariable String sku) {
        Product product = productRepository.findBySku(sku)
            .orElseThrow(() -> new SapoNotFoundException("Product not found: " + sku));
        return SapoResponse.ok(toDto(product));
    }

    private SapoProductDto toDto(Product p) {
        List<SapoVariantDto> variants = p.getVariants().stream()
            .map(v -> SapoVariantDto.builder()
                .id(v.getId())
                .sku(v.getSku())
                .size(v.getSize())
                .color(v.getColor())
                .price(v.getPrice())
                .compareAtPrice(v.getCompareAtPrice())
                .stock(v.getStock())
                .active(v.isActive())
                .build())
            .toList();

        return SapoProductDto.builder()
            .id(p.getId())
            .name(p.getName())
            .sku(p.getSku())
            .slug(p.getSlug())
            .status(p.getStatus().name())
            .category(p.getCategory() != null ? p.getCategory().getName() : null)
            .price(p.getPrice())
            .compareAtPrice(p.getCompareAtPrice())
            .variants(variants)
            .updatedAt(p.getUpdatedAt())
            .build();
    }
}
