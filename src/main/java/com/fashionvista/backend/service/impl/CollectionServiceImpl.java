package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.CollectionDetailResponse;
import com.fashionvista.backend.dto.CollectionRequest;
import com.fashionvista.backend.dto.CollectionSummaryResponse;
import com.fashionvista.backend.dto.ProductListItemDto;
import com.fashionvista.backend.entity.Collection;
import com.fashionvista.backend.entity.CollectionProduct;
import com.fashionvista.backend.entity.CollectionStatus;
import com.fashionvista.backend.repository.CollectionProductRepository;
import com.fashionvista.backend.repository.CollectionRepository;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.service.CollectionService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionSummaryResponse> getPublicCollections(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return collectionRepository.findActiveVisible(now, pageable)
            .map(CollectionSummaryResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionDetailResponse getPublicCollectionBySlug(String slug) {
        LocalDateTime now = LocalDateTime.now();

        Collection collection = collectionRepository.findBySlug(slug)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy bộ sưu tập hoặc đã hết hiệu lực."
            ));

        // Nếu đã hết hạn: tự chuyển sang ENDED và không cho truy cập public
        if (collection.getEndAt() != null && collection.getEndAt().isBefore(now)) {
            collection.setStatus(CollectionStatus.ENDED);
            collectionRepository.save(collection);
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy bộ sưu tập hoặc đã hết hiệu lực."
            );
        }

        // Chỉ allow collection đang ACTIVE và visible
        if (!collection.isVisible()
            || collection.getStatus() == null
            || collection.getStatus() != CollectionStatus.ACTIVE) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy bộ sưu tập hoặc đã hết hiệu lực."
            );
        }

        List<CollectionProduct> collectionProducts =
            collectionProductRepository.findByCollectionOrderByPositionAscIdAsc(collection);

        List<ProductListItemDto> products = collectionProducts.stream()
            .map(cp -> ProductListItemDto.fromEntity(cp.getProduct()))
            .toList();

        return CollectionDetailResponse.of(collection, products);
    }

    // ================== Admin ==================

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionSummaryResponse> searchAdminCollections(
        String keyword,
        String status,
        Boolean visible,
        Pageable pageable
    ) {
        CollectionStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = CollectionStatus.valueOf(status);
            } catch (IllegalArgumentException ignored) {
                statusEnum = null;
            }
        }
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword) {
            String keywordPattern = "%" + keyword.trim().toLowerCase() + "%";
            return collectionRepository.searchWithKeywordBase(
                    keywordPattern,
                    statusEnum,
                    visible,
                    pageable
                )
                .map(CollectionSummaryResponse::fromEntity);
        }
        return collectionRepository.searchWithoutKeywordBase(
                statusEnum,
                visible,
                pageable
            )
            .map(CollectionSummaryResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionDetailResponse getAdminCollection(Long id) {
        Collection collection = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        List<CollectionProduct> collectionProducts =
            collectionProductRepository.findByCollectionOrderByPositionAscIdAsc(collection);
        List<ProductListItemDto> products = collectionProducts.stream()
            .map(cp -> ProductListItemDto.fromEntity(cp.getProduct()))
            .toList();
        return CollectionDetailResponse.of(collection, products);
    }

    @Override
    @Transactional
    public CollectionDetailResponse createCollection(CollectionRequest request) {
        validateDates(request.getStartAt(), request.getEndAt());
        if (collectionRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug bộ sưu tập đã được sử dụng.");
        }
        Collection collection = new Collection();
        applyRequestToEntity(request, collection);
        Collection saved = collectionRepository.save(collection);
        return getAdminCollection(saved.getId());
    }

    @Override
    @Transactional
    public CollectionDetailResponse updateCollection(Long id, CollectionRequest request) {
        validateDates(request.getStartAt(), request.getEndAt());
        Collection collection = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        if (!collection.getSlug().equals(request.getSlug())
            && collectionRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug bộ sưu tập đã được sử dụng.");
        }
        applyRequestToEntity(request, collection);
        collectionRepository.save(collection);
        return getAdminCollection(id);
    }

    @Override
    @Transactional
    public void deleteCollection(Long id) {
        Collection collection = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        collectionProductRepository.deleteByCollection(collection);
        collectionRepository.delete(collection);
    }

    @Override
    @Transactional
    public void updateVisibility(Long id, boolean visible) {
        Collection collection = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        collection.setVisible(visible);
        collectionRepository.save(collection);
    }

    @Override
    @Transactional
    public void setCollectionProducts(Long id, java.util.List<Long> productIds) {
        Collection collection = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        collectionProductRepository.deleteByCollection(collection);
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        int position = 0;
        for (Long productId : productIds) {
            var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với id: " + productId));
            CollectionProduct cp = CollectionProduct.builder()
                .collection(collection)
                .product(product)
                .position(position++)
                .build();
            collectionProductRepository.save(cp);
        }
    }

    private void validateDates(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }

    private void applyRequestToEntity(CollectionRequest request, Collection collection) {
        collection.setName(request.getName());
        collection.setSlug(request.getSlug());
        collection.setDescription(request.getDescription());
        collection.setHeroImageUrl(request.getHeroImageUrl());
        collection.setLongDescriptionHtml(request.getLongDescriptionHtml());
        if (request.getStatus() != null) {
            collection.setStatus(request.getStatus());
        }
        if (request.getVisible() != null) {
            collection.setVisible(request.getVisible());
        }
        collection.setStartAt(request.getStartAt());
        collection.setEndAt(request.getEndAt());
        collection.setSeoTitle(request.getSeoTitle());
        collection.setSeoDescription(request.getSeoDescription());
    }
}


