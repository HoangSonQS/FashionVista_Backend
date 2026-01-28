package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.ProductCreateRequest;
import com.fashionvista.backend.dto.ProductDetailDto;
import com.fashionvista.backend.dto.ProductImageDto;
import com.fashionvista.backend.dto.ProductListItemDto;
import com.fashionvista.backend.dto.ProductListResponse;
import com.fashionvista.backend.dto.ProductUpdateRequest;
import com.fashionvista.backend.dto.ProductVariantDto;
import com.fashionvista.backend.dto.ProductVariantRequest;
import com.fashionvista.backend.dto.SearchSuggestionDto;
import com.fashionvista.backend.entity.Category;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductImage;
import com.fashionvista.backend.entity.ProductStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.CategoryRepository;
import com.fashionvista.backend.repository.ProductImageRepository;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.CloudinaryService;
import com.fashionvista.backend.service.ProductService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public ProductListResponse getProducts(
            String categorySlug,
            String search,
            String size,
            String color,
            Double minPrice,
            Double maxPrice,
            int page,
            int sizePage) {
        Specification<Product> specification = buildSpecification(categorySlug, search, size, color, minPrice, maxPrice,
                ProductStatus.ACTIVE, null, true);
        Pageable pageable = PageRequest.of(page, sizePage, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> products = productRepository.findAll(specification, pageable);
        List<Product> hydrated = hydrateWithImages(products);

        List<ProductListItemDto> items = hydrated.stream()
                .map(this::toListItemDto)
                .toList();

        return ProductListResponse.builder()
                .items(items)
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .page(products.getNumber())
                .size(products.getSize())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailDto getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        return toDetailDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchSuggestionDto> searchSuggestions(String keyword) {
        Specification<Product> spec = (root, query, cb) -> {
            String likeExpression = "%" + keyword.toLowerCase() + "%";
            Predicate predicate = cb.or(
                    cb.like(cb.lower(root.get("name")), likeExpression),
                    cb.like(cb.lower(root.get("description")), likeExpression));
            return cb.and(
                    predicate,
                    cb.equal(root.get("status"), ProductStatus.ACTIVE));
        };

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> pageResult = productRepository.findAll(spec, pageable);
        List<Product> hydrated = hydrateWithImages(pageResult);
        return hydrated.stream()
                .map(product -> SearchSuggestionDto.builder()
                        .slug(product.getSlug())
                        .name(product.getName())
                        .thumbnailUrl(resolveThumbnail(product))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public ProductDetailDto createProduct(ProductCreateRequest request, List<MultipartFile> images) {
        List<String> uploadedPublicIds = new ArrayList<>();
        try {
            Category category = null;
            if (request.getCategorySlug() != null && !request.getCategorySlug().isBlank()) {
                category = categoryRepository.findBySlug(request.getCategorySlug())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
            }

            Product product = Product.builder()
                    .name(request.getName())
                    .slug(request.getSlug())
                    .description(request.getDescription())
                    .shortDescription(request.getShortDescription())
                    .price(request.getPrice())
                    .compareAtPrice(request.getCompareAtPrice())
                    .sku(request.getSku())
                    .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                    .featured(request.isFeatured())
                    .category(category)
                    .tags(request.getTags() != null ? new ArrayList<>(request.getTags()) : new ArrayList<>())
                    .sizes(request.getSizes() != null ? new ArrayList<>(request.getSizes()) : new ArrayList<>())
                    .colors(request.getColors() != null ? new ArrayList<>(request.getColors()) : new ArrayList<>())
                    .build();

            if (request.getVariants() != null && !request.getVariants().isEmpty()) {
                request.getVariants()
                        .forEach(variantRequest -> product.getVariants().add(toVariantEntity(product, variantRequest)));
            } else {
                product.getVariants().add(ProductVariant.builder()
                        .product(product)
                        .sku(request.getSku() + "-DEFAULT")
                        .price(request.getPrice())
                        .stock(0)
                        .isActive(true)
                        .build());
            }

            Product saved = productRepository.save(product);

            if (images != null) {
                int index = 0;
                for (MultipartFile imageFile : images) {
                    if (imageFile == null || imageFile.isEmpty()) {
                        continue;
                    }
                    CloudinaryService.CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(imageFile);
                    uploadedPublicIds.add(uploadResult.publicId());
                    ProductImage productImage = ProductImage.builder()
                            .product(saved)
                            .url(uploadResult.secureUrl())
                            .cloudinaryPublicId(uploadResult.publicId())
                            .isPrimary(index == 0)
                            .order(index)
                            .build();
                    saved.getImages().add(productImage);
                    index++;
                }
                saved = productRepository.save(saved);
            }

            return getProductBySlug(saved.getSlug());
        } catch (RuntimeException e) {
            cleanupUploadedImages(uploadedPublicIds);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductListResponse getAdminProducts(
            String categorySlug,
            String search,
            ProductStatus status,
            Boolean featured,
            Boolean visible,
            int page,
            int sizePage) {
        Specification<Product> specification = buildSpecification(categorySlug, search, null, null, null, null, status,
                featured, visible);
        Pageable pageable = PageRequest.of(page, sizePage, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Product> products = productRepository.findAll(specification, pageable);
        List<Product> hydrated = hydrateWithImages(products);

        List<ProductListItemDto> items = hydrated.stream()
                .map(this::toListItemDto)
                .toList();

        return ProductListResponse.builder()
                .items(items)
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .page(products.getNumber())
                .size(products.getSize())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        return toDetailDto(product);
    }

    @Override
    @Transactional
    public ProductDetailDto updateProduct(Long id, ProductUpdateRequest request, List<MultipartFile> images) {
        List<String> uploadedPublicIds = new ArrayList<>();
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

            Category category = null;
            if (request.getCategorySlug() != null && !request.getCategorySlug().isBlank()) {
                category = categoryRepository.findBySlug(request.getCategorySlug())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
            }

            product.setName(request.getName());
            product.setSlug(request.getSlug());
            product.setDescription(request.getDescription());
            product.setShortDescription(request.getShortDescription());
            product.setPrice(request.getPrice());
            product.setCompareAtPrice(request.getCompareAtPrice());
            product.setSku(request.getSku());
            product.setStatus(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE);
            product.setFeatured(request.isFeatured());
            product.setCategory(category);
            product.setTags(request.getTags() != null ? new ArrayList<>(request.getTags()) : new ArrayList<>());
            product.setSizes(request.getSizes() != null ? new ArrayList<>(request.getSizes()) : new ArrayList<>());
            product.setColors(request.getColors() != null ? new ArrayList<>(request.getColors()) : new ArrayList<>());

            if (request.getVariants() != null) {
                applyVariantChanges(product, request.getVariants());
            }

            if (request.getRemovedImageIds() != null && !request.getRemovedImageIds().isEmpty()) {
                removeProductImages(product, request.getRemovedImageIds());
            }

            if (images != null) {
                int nextOrder = product.getImages().size();
                for (MultipartFile imageFile : images) {
                    if (imageFile == null || imageFile.isEmpty()) {
                        continue;
                    }
                    CloudinaryService.CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(imageFile);
                    uploadedPublicIds.add(uploadResult.publicId());
                    ProductImage productImage = ProductImage.builder()
                            .product(product)
                            .url(uploadResult.secureUrl())
                            .cloudinaryPublicId(uploadResult.publicId())
                            .isPrimary(product.getImages().isEmpty() && nextOrder == 0)
                            .order(nextOrder++)
                            .build();
                    product.getImages().add(productImage);
                }
            }

            productRepository.save(product);
            return toDetailDto(product);
        } catch (RuntimeException ex) {
            cleanupUploadedImages(uploadedPublicIds);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void updateProductStatus(Long id, ProductStatus status, Boolean featured) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        if (status != null) {
            product.setStatus(status);
        }
        if (featured != null) {
            product.setFeatured(featured);
        }
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void updateProductVisibility(Long id, boolean visible) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        // Validation: không cho bật Visible nếu thiếu ảnh/giá/tồn kho
        if (visible) {
            if (product.getImages() == null || product.getImages().isEmpty()) {
                throw new IllegalArgumentException("Không thể bật hiển thị: Sản phẩm chưa có ảnh đại diện.");
            }
            if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Không thể bật hiển thị: Sản phẩm chưa có giá hợp lệ.");
            }
            if (product.getVariants() == null || product.getVariants().isEmpty()) {
                throw new IllegalArgumentException("Không thể bật hiển thị: Sản phẩm chưa có biến thể.");
            }
            long totalStock = product.getVariants().stream()
                    .mapToLong(v -> v.getStock() != null ? v.getStock() : 0)
                    .sum();
            if (totalStock <= 0) {
                throw new IllegalArgumentException("Không thể bật hiển thị: Tổng tồn kho = 0.");
            }
        }

        product.setVisible(visible);
        product.setVisibleUpdatedAt(java.time.LocalDateTime.now());
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void updateProductVisibilityBulk(List<Long> productIds, boolean visible) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) {
            throw new IllegalArgumentException("Một số sản phẩm không tồn tại.");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Product product : products) {
            // Validation tương tự như updateProductVisibility
            if (visible) {
                if (product.getImages() == null || product.getImages().isEmpty()) {
                    continue; // Skip products without images
                }
                if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Skip products without valid price
                }
                if (product.getVariants() == null || product.getVariants().isEmpty()) {
                    continue; // Skip products without variants
                }
                long totalStock = product.getVariants().stream()
                        .mapToLong(v -> v.getStock() != null ? v.getStock() : 0)
                        .sum();
                if (totalStock <= 0) {
                    continue; // Skip products without stock
                }
            }

            product.setVisible(visible);
            product.setVisibleUpdatedAt(now);
        }

        productRepository.saveAll(products);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        product.setStatus(ProductStatus.INACTIVE);
        product.setFeatured(false);
        productRepository.save(product);
    }

    private Specification<Product> buildSpecification(
            String categorySlug,
            String search,
            String size,
            String color,
            Double minPrice,
            Double maxPrice,
            ProductStatus statusFilter,
            Boolean featuredFilter,
            Boolean visibleFilter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (categorySlug != null && !categorySlug.isBlank()) {
                Join<Object, Object> categoryJoin = root.join("category", JoinType.LEFT);
                predicates.add(cb.equal(cb.lower(categoryJoin.get("slug")), categorySlug.toLowerCase()));
            }

            if (search != null && !search.isBlank()) {
                String likeExpression = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likeExpression),
                        cb.like(cb.lower(root.get("description")), likeExpression)));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), BigDecimal.valueOf(minPrice)));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), BigDecimal.valueOf(maxPrice)));
            }

            if ((size != null && !size.isBlank()) || (color != null && !color.isBlank())) {
                Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.LEFT);
                if (size != null && !size.isBlank()) {
                    predicates.add(cb.equal(cb.lower(variantJoin.get("size")), size.toLowerCase()));
                }
                if (color != null && !color.isBlank()) {
                    predicates.add(cb.equal(cb.lower(variantJoin.get("color")), color.toLowerCase()));
                }
            }

            if (featuredFilter != null) {
                predicates.add(cb.equal(root.get("featured"), featuredFilter));
            }

            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }

            if (visibleFilter != null) {
                predicates.add(cb.equal(root.get("isVisible"), visibleFilter));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ProductListItemDto toListItemDto(Product product) {
        Integer totalStock = productVariantRepository.sumStockByProductId(product.getId());

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
                .thumbnailUrl(resolveThumbnail(product))
                .category(product.getCategory() != null ? product.getCategory().getName() : null)
                .build();
    }

    private ProductDetailDto toDetailDto(Product product) {
        return ProductDetailDto.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .sku(product.getSku())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .price(product.getPrice())
                .compareAtPrice(product.getCompareAtPrice())
                .status(product.getStatus().name())
                .featured(product.isFeatured())
                .category(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .tags(product.getTags())
                .sizes(product.getSizes())
                .colors(product.getColors())
                .images(product.getImages().stream()
                        .map(image -> ProductImageDto.builder()
                                .id(image.getId())
                                .url(image.getUrl())
                                .alt(image.getAlt())
                                .primary(image.isPrimary())
                                .build())
                        .toList())
                .variants(product.getVariants().stream()
                        .map(this::toVariantDto)
                        .toList())
                .build();
    }

    private void applyVariantChanges(Product product, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null) {
            return;
        }

        if (variantRequests.isEmpty()) {
            product.getVariants().forEach(productVariantRepository::delete);
            product.getVariants().clear();
            product.getVariants().add(ProductVariant.builder()
                    .product(product)
                    .sku(product.getSku() + "-DEFAULT")
                    .price(product.getPrice())
                    .stock(0)
                    .isActive(true)
                    .build());
            return;
        }

        List<ProductVariant> existingVariants = new ArrayList<>(product.getVariants());
        List<Long> keepIds = new ArrayList<>();

        for (ProductVariantRequest variantRequest : variantRequests) {
            if (variantRequest.getId() != null) {
                ProductVariant existing = existingVariants.stream()
                        .filter(variant -> variantRequest.getId().equals(variant.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể."));
                existing.setSize(variantRequest.getSize());
                existing.setColor(variantRequest.getColor());
                existing.setSku(variantRequest.getSku());
                existing.setStock(variantRequest.getStock());
                existing.setPrice(variantRequest.getPrice());
                existing.setActive(variantRequest.isActive());
                keepIds.add(existing.getId());
            } else {
                ProductVariant newVariant = toVariantEntity(product, variantRequest);
                product.getVariants().add(newVariant);
            }
        }

        for (ProductVariant variant : existingVariants) {
            if (variant.getId() != null && !keepIds.contains(variant.getId())) {
                product.getVariants().remove(variant);
                productVariantRepository.delete(variant);
            }
        }
    }

    private void removeProductImages(Product product, List<Long> removedImageIds) {
        if (removedImageIds == null || removedImageIds.isEmpty()) {
            return;
        }
        product.getImages().removeIf(image -> {
            if (image.getId() != null && removedImageIds.contains(image.getId())) {
                if (image.getCloudinaryPublicId() != null) {
                    try {
                        cloudinaryService.deleteImage(image.getCloudinaryPublicId());
                    } catch (Exception ex) {
                        log.warn("Không thể xóa ảnh Cloudinary {}: {}", image.getCloudinaryPublicId(), ex.getMessage());
                    }
                }
                productImageRepository.delete(image);
                return true;
            }
            return false;
        });
    }

    private ProductVariantDto toVariantDto(ProductVariant variant) {
        return ProductVariantDto.builder()
                .id(variant.getId())
                .size(variant.getSize())
                .color(variant.getColor())
                .sku(variant.getSku())
                .price(variant.getPrice() != null ? variant.getPrice() : variant.getProduct().getPrice())
                .stock(variant.getStock())
                .active(variant.isActive())
                .build();
    }

    private ProductVariant toVariantEntity(Product product, ProductVariantRequest request) {
        return ProductVariant.builder()
                .product(product)
                .size(request.getSize())
                .color(request.getColor())
                .sku(request.getSku())
                .price(request.getPrice())
                .stock(Objects.requireNonNullElse(request.getStock(), 0))
                .isActive(request.isActive())
                .build();
    }

    private void cleanupUploadedImages(List<String> publicIds) {
        publicIds.forEach(publicId -> {
            try {
                cloudinaryService.deleteImage(publicId);
            } catch (Exception ex) {
                log.warn("Không thể xóa ảnh Cloudinary {} sau khi thất bại: {}", publicId, ex.getMessage());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListItemDto> getFeaturedProducts(int limit) {
        Specification<Product> spec = buildSpecification(null, null, null, null, null, null, ProductStatus.ACTIVE, true,
                true);
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Product> products = productRepository.findAll(spec, pageable);
        List<Product> hydrated = hydrateWithImages(products);
        return hydrated.stream()
                .map(this::toListItemDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListItemDto> getNewArrivals(int limit) {
        Specification<Product> spec = buildSpecification(null, null, null, null, null, null, ProductStatus.ACTIVE, null,
                true);
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> products = productRepository.findAll(spec, pageable);
        List<Product> hydrated = hydrateWithImages(products);
        return hydrated.stream()
                .map(this::toListItemDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListItemDto> getSaleProducts(int limit) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Chỉ lấy sản phẩm đang ACTIVE
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));

            // Điều kiện đang sale: compareAtPrice > price và compareAtPrice không null
            predicates.add(cb.isNotNull(root.get("compareAtPrice")));
            predicates.add(cb.greaterThan(root.get("compareAtPrice"), root.get("price")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Product> products = productRepository.findAll(spec, pageable);
        List<Product> hydrated = hydrateWithImages(products);

        return hydrated.stream()
                .map(this::toListItemDto)
                .toList();
    }

    /**
     * Hydrate products with images (thumbnail) using a second query to tránh fetch
     * join + paging warning.
     */
    private List<Product> hydrateWithImages(Page<Product> page) {
        List<Long> ids = page.getContent().stream()
                .map(Product::getId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> fetched = productRepository.findAllWithImagesByIdIn(ids);
        Map<Long, Product> byId = new HashMap<>();
        fetched.forEach(p -> byId.put(p.getId(), p));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private String resolveThumbnail(Product product) {
        return product.getImages().stream()
                .filter(image -> image.isPrimary() && image.getUrl() != null)
                .map(image -> image.getUrl())
                .findFirst()
                .orElseGet(() -> product.getImages().stream()
                        .map(image -> image.getUrl())
                        .findFirst()
                        .orElse(null));
    }
}
