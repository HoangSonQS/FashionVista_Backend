package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductListItemDto {

    Long id;
    String name;
    String slug;
    String sku;
    BigDecimal price;
    BigDecimal compareAtPrice;
    String status;
    boolean featured;
    Boolean isVisible;
    Integer variantsCount;
    Integer totalStock; // Tổng stock của tất cả variants (bao gồm cả inactive)
    java.time.LocalDateTime visibleUpdatedAt;
    String thumbnailUrl;
    String category;

    /**
     * Legacy factory used by CollectionServiceImpl and other callers.
     * Tính tổng stock từ danh sách variants hiện có của entity.
     * Lưu ý: tổng này bao gồm cả variants inactive, và không phụ thuộc repository.
     */
    public static ProductListItemDto fromEntity(com.fashionvista.backend.entity.Product product) {
        Integer totalStock = 0;
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            totalStock = product.getVariants().stream()
                .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                .sum();
        }

        return ProductListItemDto.builder()
            .id(product.getId())
            .name(product.getName())
            .slug(product.getSlug())
            .sku(product.getSku())
            .price(product.getPrice())
            .compareAtPrice(product.getCompareAtPrice())
            .status(product.getStatus().name())
            .featured(product.isFeatured())
            .isVisible(product.isVisible())
            .variantsCount(product.getVariants() != null ? product.getVariants().size() : 0)
            .totalStock(totalStock)
            .visibleUpdatedAt(product.getVisibleUpdatedAt())
            .thumbnailUrl(product.getImages().stream()
                .filter(image -> Boolean.TRUE.equals(image.isPrimary()))
                .findFirst()
                .map(com.fashionvista.backend.entity.ProductImage::getUrl)
                .orElse(null))
            .category(product.getCategory() != null ? product.getCategory().getName() : null)
            .build();
    }
}

