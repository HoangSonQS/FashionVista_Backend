package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.CollectionDetailResponse;
import com.fashionvista.backend.dto.CollectionRequest;
import com.fashionvista.backend.dto.CollectionSummaryResponse;
import com.fashionvista.backend.dto.ProductListItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CollectionService {

    Page<CollectionSummaryResponse> getPublicCollections(Pageable pageable);

    CollectionDetailResponse getPublicCollectionBySlug(String slug);

    // Admin
    Page<CollectionSummaryResponse> searchAdminCollections(
        String keyword,
        String status,
        Boolean visible,
        Pageable pageable
    );

    CollectionDetailResponse getAdminCollection(Long id);

    CollectionDetailResponse createCollection(CollectionRequest request);

    CollectionDetailResponse updateCollection(Long id, CollectionRequest request);

    void deleteCollection(Long id);

    void updateVisibility(Long id, boolean visible);

    void setCollectionProducts(Long id, java.util.List<Long> productIds);

    // Collection Products Management
    Page<ProductListItemDto> getCollectionProducts(Long collectionId, Pageable pageable);

    void addProductToCollection(Long collectionId, Long productId);

    void removeProductFromCollection(Long collectionId, Long productId);

    void reorderCollectionProducts(Long collectionId, java.util.List<Long> productIds);

    void bulkAddRemoveProducts(Long collectionId, java.util.List<Long> addProductIds, java.util.List<Long> removeProductIds);
}


