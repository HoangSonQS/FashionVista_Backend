package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.Collection;
import com.fashionvista.backend.entity.CollectionStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectionDetailResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String longDescriptionHtml;
    private String heroImageUrl;
    private CollectionStatus status;
    private boolean visible;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private List<ProductListItemDto> products;

    public static CollectionDetailResponse of(Collection collection, List<ProductListItemDto> products) {
        return CollectionDetailResponse.builder()
            .id(collection.getId())
            .name(collection.getName())
            .slug(collection.getSlug())
            .description(collection.getDescription())
            .longDescriptionHtml(collection.getLongDescriptionHtml())
            .heroImageUrl(collection.getHeroImageUrl())
            .status(collection.getStatus())
            .visible(collection.isVisible())
            .startAt(collection.getStartAt())
            .endAt(collection.getEndAt())
            .products(products)
            .build();
    }
}


