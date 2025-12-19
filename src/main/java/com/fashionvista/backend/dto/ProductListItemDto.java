package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.Product;
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
    java.time.LocalDateTime visibleUpdatedAt;
    String thumbnailUrl;
    String category;

    public static ProductListItemDto fromEntity(Product product) {
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
            .visibleUpdatedAt(product.getVisibleUpdatedAt())
            .thumbnailUrl(product.getImages().stream()
                .filter(image -> Boolean.TRUE.equals(image.isPrimary()))
                .findFirst()
                .map(img -> img.getUrl())
                .orElse(null))
            .category(product.getCategory() != null ? product.getCategory().getName() : null)
            .build();
    }
}

