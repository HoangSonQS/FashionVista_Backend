package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.CollectionDetailResponse;
import com.fashionvista.backend.dto.CollectionRequest;
import com.fashionvista.backend.dto.CollectionSummaryResponse;
import com.fashionvista.backend.dto.ProductListItemDto;
import com.fashionvista.backend.entity.Collection;
import com.fashionvista.backend.entity.CollectionProduct;
import com.fashionvista.backend.entity.CollectionStatus;
import com.fashionvista.backend.entity.Product;
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
                        "Không tìm thấy bộ sưu tập hoặc đã hết hiệu lực."));

        // Nếu đã hết hạn: tự chuyển sang ENDED và không cho truy cập public
        if (collection.getEndAt() != null && collection.getEndAt().isBefore(now)) {
            collection.setStatus(CollectionStatus.ENDED);
            collectionRepository.save(collection);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy bộ sưu tập hoặc đã hết hiệu lực.");
        }

        // Chỉ allow collection đang ACTIVE và visible
        if (!collection.isVisible()
                || collection.getStatus() == null
                || collection.getStatus() != CollectionStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy bộ sưu tập hoặc đã hết hiệu lực.");
        }

        List<CollectionProduct> collectionProducts = collectionProductRepository
                .findByCollectionOrderByPositionAscIdAsc(collection);

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
            Pageable pageable) {
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
                    pageable)
                    .map(CollectionSummaryResponse::fromEntity);
        }
        return collectionRepository.searchWithoutKeywordBase(
                statusEnum,
                visible,
                pageable)
                .map(CollectionSummaryResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionDetailResponse getAdminCollection(Long id) {
        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        List<CollectionProduct> collectionProducts = collectionProductRepository
                .findByCollectionOrderByPositionAscIdAsc(collection);
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

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListItemDto> getCollectionProducts(Long collectionId, Pageable pageable) {
        collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        Page<CollectionProduct> collectionProducts = collectionProductRepository
                .findByCollectionIdOrderByPositionAscIdAsc(collectionId, pageable);
        return collectionProducts.map(cp -> ProductListItemDto.fromEntity(cp.getProduct()));
    }

    @Override
    @Transactional
    public void addProductToCollection(Long collectionId, Long productId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với id: " + productId));

        // Kiểm tra xem sản phẩm đã có trong collection chưa
        if (collectionProductRepository.findByCollectionAndProduct(collection, product).isPresent()) {
            throw new IllegalArgumentException("Sản phẩm đã có trong bộ sưu tập.");
        }

        // Tìm position lớn nhất và thêm vào cuối
        List<CollectionProduct> existingProducts = collectionProductRepository
                .findByCollectionOrderByPositionAscIdAsc(collection);
        int maxPosition = existingProducts.stream()
                .mapToInt(CollectionProduct::getPosition)
                .max()
                .orElse(-1);

        CollectionProduct cp = CollectionProduct.builder()
                .collection(collection)
                .product(product)
                .position(maxPosition + 1)
                .build();
        collectionProductRepository.save(cp);
    }

    @Override
    @Transactional
    public void removeProductFromCollection(Long collectionId, Long productId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với id: " + productId));

        CollectionProduct cp = collectionProductRepository.findByCollectionAndProduct(collection, product)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không có trong bộ sưu tập."));

        collectionProductRepository.delete(cp);

        // Reorder các sản phẩm còn lại
        reorderPositions(collection);
    }

    @Override
    @Transactional
    public void reorderCollectionProducts(Long collectionId, java.util.List<Long> productIds) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));

        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        // Validate tất cả productIds đều tồn tại trong collection
        List<CollectionProduct> existingProducts = collectionProductRepository
                .findByCollectionOrderByPositionAscIdAsc(collection);
        java.util.Set<Long> existingProductIds = existingProducts.stream()
                .map(cp -> cp.getProduct().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (Long productId : productIds) {
            if (!existingProductIds.contains(productId)) {
                throw new IllegalArgumentException("Sản phẩm với id " + productId + " không có trong bộ sưu tập.");
            }
        }

        // Update positions theo thứ tự mới
        java.util.Map<Long, CollectionProduct> productMap = existingProducts.stream()
                .collect(java.util.stream.Collectors.toMap(cp -> cp.getProduct().getId(), cp -> cp));

        int position = 0;
        for (Long productId : productIds) {
            CollectionProduct cp = productMap.get(productId);
            cp.setPosition(position++);
            collectionProductRepository.save(cp);
        }
    }

    @Override
    @Transactional
    public void bulkAddRemoveProducts(Long collectionId, java.util.List<Long> addProductIds,
            java.util.List<Long> removeProductIds) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ sưu tập."));

        // Remove products
        if (removeProductIds != null && !removeProductIds.isEmpty()) {
            for (Long productId : removeProductIds) {
                Product product = productRepository.findById(productId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy sản phẩm với id: " + productId));
                collectionProductRepository.findByCollectionAndProduct(collection, product)
                        .ifPresent(collectionProductRepository::delete);
            }
        }

        // Add products
        if (addProductIds != null && !addProductIds.isEmpty()) {
            List<CollectionProduct> existingProducts = collectionProductRepository
                    .findByCollectionOrderByPositionAscIdAsc(collection);
            int maxPosition = existingProducts.stream()
                    .mapToInt(CollectionProduct::getPosition)
                    .max()
                    .orElse(-1);

            int position = maxPosition + 1;
            for (Long productId : addProductIds) {
                Product product = productRepository.findById(productId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy sản phẩm với id: " + productId));

                // Kiểm tra xem đã có chưa
                if (collectionProductRepository.findByCollectionAndProduct(collection, product).isEmpty()) {
                    CollectionProduct cp = CollectionProduct.builder()
                            .collection(collection)
                            .product(product)
                            .position(position++)
                            .build();
                    collectionProductRepository.save(cp);
                }
            }
        }

        // Reorder sau khi add/remove
        reorderPositions(collection);
    }

    private void reorderPositions(Collection collection) {
        List<CollectionProduct> products = collectionProductRepository
                .findByCollectionOrderByPositionAscIdAsc(collection);
        int position = 0;
        for (CollectionProduct cp : products) {
            cp.setPosition(position++);
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
