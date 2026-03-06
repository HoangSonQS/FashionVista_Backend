package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminProductVariantCreateRequest;
import com.fashionvista.backend.dto.AdminProductVariantResponse;
import com.fashionvista.backend.dto.AdminProductVariantUpdateRequest;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.AdminProductVariantService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AdminProductVariantServiceImpl implements AdminProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminProductVariantResponse> getAll(
            Long productId,
            String search,
            String size,
            String color,
            Boolean active,
            Integer minStock,
            Pageable pageable) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }

            if (search != null && !search.trim().isEmpty()) {
                String likeExpression = "%" + search.trim().toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("sku")), likeExpression),
                                cb.like(cb.lower(root.get("size")), likeExpression),
                                cb.like(cb.lower(root.get("color")), likeExpression),
                                cb.like(cb.lower(root.get("product").get("name")), likeExpression)));
            }

            if (size != null && !size.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("size")), size.trim().toLowerCase()));
            }

            if (color != null && !color.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("color")), color.trim().toLowerCase()));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }

            if (minStock != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stock"), minStock));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return productVariantRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductVariantResponse getById(Long id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy biến thể với ID: " + id));
        return toResponse(variant);
    }

    @Override
    @Transactional
    public AdminProductVariantResponse create(AdminProductVariantCreateRequest request) {
        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(
                        () -> new EntityNotFoundException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));

        // Check SKU uniqueness
        if (productVariantRepository.findBySku(request.getSku()).isPresent()) {
            throw new IllegalArgumentException("SKU đã tồn tại: " + request.getSku());
        }

        // Rule 1: Inherit product prices when variant prices not explicitly provided
        BigDecimal price = (request.getPrice() != null && request.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0)
                ? request.getPrice()
                : product.getPrice();
        BigDecimal compareAtPrice = request.getCompareAtPrice() != null
                ? request.getCompareAtPrice()
                : product.getCompareAtPrice();

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .size(request.getSize())
                .color(request.getColor())
                .sku(request.getSku())
                .price(price)
                .compareAtPrice(compareAtPrice)
                .stock(request.getStock())
                .isActive(request.isActive())
                .build();

        variant = productVariantRepository.save(variant);
        return toResponse(variant);
    }

    @Override
    @Transactional
    public AdminProductVariantResponse update(Long id, AdminProductVariantUpdateRequest request) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy biến thể với ID: " + id));

        // Check SKU uniqueness if SKU is being changed
        if (!variant.getSku().equals(request.getSku())) {
            if (productVariantRepository.findBySku(request.getSku()).isPresent()) {
                throw new IllegalArgumentException("SKU đã tồn tại: " + request.getSku());
            }
        }

        variant.setSize(request.getSize());
        variant.setColor(request.getColor());
        variant.setSku(request.getSku());
        if (request.getStock() != null) {
            variant.setStock(request.getStock());
        }
        if (request.getPrice() != null) {
            variant.setPrice(request.getPrice());
        }
        if (request.getCompareAtPrice() != null) {
            variant.setCompareAtPrice(request.getCompareAtPrice());
        }
        if (request.getActive() != null) {
            variant.setActive(request.getActive());
        }

        variant = productVariantRepository.save(variant);
        return toResponse(variant);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy biến thể với ID: " + id));

        productVariantRepository.delete(variant);
    }

    @Override
    @Transactional
    public AdminProductVariantResponse updateStock(Long id, Integer stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("Stock phải >= 0");
        }

        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy biến thể với ID: " + id));

        variant.setStock(stock);
        variant = productVariantRepository.save(variant);
        return toResponse(variant);
    }

    @Override
    @Transactional
    public AdminProductVariantResponse updatePrice(Long id, BigDecimal price) {
        if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price phải >= 0");
        }

        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy biến thể với ID: " + id));

        variant.setPrice(price);
        variant = productVariantRepository.save(variant);
        return toResponse(variant);
    }

    private AdminProductVariantResponse toResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        return AdminProductVariantResponse.builder()
                .id(variant.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productSlug(product.getSlug())
                .size(variant.getSize())
                .color(variant.getColor())
                .sku(variant.getSku())
                .price(variant.getPrice())
                .compareAtPrice(variant.getCompareAtPrice())
                .stock(variant.getStock())
                .active(variant.isActive())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }
}
