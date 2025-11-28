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
import com.fashionvista.backend.repository.CartItemRepository;
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
    private final CartItemRepository cartItemRepository;
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
        int sizePage
    ) {
        Specification<Product> specification = buildSpecification(categorySlug, search, size, color, minPrice, maxPrice, ProductStatus.ACTIVE, null);
        Pageable pageable = PageRequest.of(page, sizePage, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> products = productRepository.findAll(specification, pageable);

        List<ProductListItemDto> items = products.stream()
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
                cb.like(cb.lower(root.get("description")), likeExpression)
            );
            return cb.and(
                predicate,
                cb.equal(root.get("status"), ProductStatus.ACTIVE)
            );
        };

        Pageable pageable = PageRequest.of(0, 10);
        return productRepository.findAll(spec, pageable).stream()
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
                request.getVariants().forEach(variantRequest -> product.getVariants().add(toVariantEntity(product, variantRequest)));
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
        int page,
        int sizePage
    ) {
        Specification<Product> specification = buildSpecification(categorySlug, search, null, null, null, null, status, featured);
        Pageable pageable = PageRequest.of(page, sizePage, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Product> products = productRepository.findAll(specification, pageable);

        List<ProductListItemDto> items = products.stream()
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

            // Validate SKU: không được trùng với sản phẩm khác
            if (!product.getSku().equals(request.getSku())) {
                productRepository.findBySku(request.getSku())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new IllegalArgumentException("SKU đã được sử dụng bởi sản phẩm khác.");
                        }
                    });
            }

            // Validate Slug: không được trùng với sản phẩm khác
            if (!product.getSlug().equals(request.getSlug())) {
                productRepository.findBySlug(request.getSlug())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new IllegalArgumentException("Slug đã được sử dụng bởi sản phẩm khác.");
                        }
                    });
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
        Boolean featuredFilter
    ) {
        return (root, query, cb) -> {
            if (Product.class.equals(query.getResultType())) {
                root.fetch("images", JoinType.LEFT);
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();

            if (categorySlug != null && !categorySlug.isBlank()) {
                Join<Object, Object> categoryJoin = root.join("category", JoinType.LEFT);
                predicates.add(cb.equal(cb.lower(categoryJoin.get("slug")), categorySlug.toLowerCase()));
            }

            if (search != null && !search.isBlank()) {
                String likeExpression = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), likeExpression),
                    cb.like(cb.lower(root.get("description")), likeExpression)
                ));
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ProductListItemDto toListItemDto(Product product) {
        return ProductListItemDto.builder()
            .id(product.getId())
            .name(product.getName())
            .slug(product.getSlug())
            .sku(product.getSku())
            .price(product.getPrice())
            .compareAtPrice(product.getCompareAtPrice())
            .status(product.getStatus().name())
            .featured(product.isFeatured())
            .thumbnailUrl(resolveThumbnail(product))
            .category(product.getCategory() != null ? product.getCategory().getName() : null)
            .build();
    }

    private ProductDetailDto toDetailDto(Product product) {
        return ProductDetailDto.builder()
            .id(product.getId())
            .name(product.getName())
            .slug(product.getSlug())
            .description(product.getDescription())
            .shortDescription(product.getShortDescription())
            .price(product.getPrice())
            .compareAtPrice(product.getCompareAtPrice())
            .status(product.getStatus().name())
            .featured(product.isFeatured())
            .category(product.getCategory() != null ? product.getCategory().getName() : null)
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
            // Đánh dấu inactive thay vì xóa để tránh conflict với cart_items
            product.getVariants().forEach(variant -> {
                variant.setActive(false);
                productVariantRepository.save(variant);
            });
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
                
                // Validate variant SKU: không được trùng với variant khác
                if (!existing.getSku().equals(variantRequest.getSku())) {
                    productVariantRepository.findBySku(variantRequest.getSku())
                        .ifPresent(conflict -> {
                            if (!conflict.getId().equals(existing.getId())) {
                                throw new IllegalArgumentException("SKU biến thể '" + variantRequest.getSku() + "' đã được sử dụng.");
                            }
                        });
                }
                
                existing.setSize(variantRequest.getSize());
                existing.setColor(variantRequest.getColor());
                existing.setSku(variantRequest.getSku());
                existing.setStock(variantRequest.getStock());
                existing.setPrice(variantRequest.getPrice());
                existing.setActive(variantRequest.isActive());
                keepIds.add(existing.getId());
            } else {
                // Validate new variant SKU: không được trùng với variant khác
                productVariantRepository.findBySku(variantRequest.getSku())
                    .ifPresent(conflict -> {
                        throw new IllegalArgumentException("SKU biến thể '" + variantRequest.getSku() + "' đã được sử dụng.");
                    });
                
                ProductVariant newVariant = toVariantEntity(product, variantRequest);
                product.getVariants().add(newVariant);
            }
        }

        for (ProductVariant variant : existingVariants) {
            if (variant.getId() != null && !keepIds.contains(variant.getId())) {
                // Kiểm tra xem variant có đang được sử dụng trong cart_items không
                boolean isUsedInCart = cartItemRepository.existsByVariantId(variant.getId());
                
                if (isUsedInCart) {
                    // Nếu đang được sử dụng, chỉ đánh dấu inactive thay vì xóa
                    variant.setActive(false);
                    productVariantRepository.save(variant);
                    product.getVariants().remove(variant);
                } else {
                    // Nếu không được sử dụng, có thể xóa
                    product.getVariants().remove(variant);
                    productVariantRepository.delete(variant);
                }
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

