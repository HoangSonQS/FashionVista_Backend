package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminCategoryResponse;
import com.fashionvista.backend.dto.CategoryCreateRequest;
import com.fashionvista.backend.dto.CategoryUpdateRequest;
import com.fashionvista.backend.entity.Category;
import com.fashionvista.backend.repository.CategoryRepository;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.service.AdminCategoryService;
import com.fashionvista.backend.service.CloudinaryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminCategoryResponse> getAllCategories(String search, Boolean isActive, Pageable pageable) {
        Specification<Category> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("slug")), searchPattern)
                )
            );
        }

        if (isActive != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }

        Page<Category> categories = categoryRepository.findAll(spec, pageable);

        return categories.map(this::toAdminCategoryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục với ID: " + id));
        return toAdminCategoryResponse(category);
    }

    @Override
    @Transactional
    public AdminCategoryResponse createCategory(CategoryCreateRequest request, MultipartFile imageFile) {
        // Kiểm tra slug trùng
        if (categoryRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Slug đã tồn tại: " + request.getSlug());
        }

        String imageUrl = request.getImage();
        String cloudinaryPublicId = null;

        // Upload ảnh lên Cloudinary nếu có file
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                var uploadResult = cloudinaryService.uploadCategoryImage(imageFile);
                imageUrl = uploadResult.secureUrl();
                cloudinaryPublicId = uploadResult.publicId();
            } catch (Exception e) {
                log.error("Lỗi khi upload ảnh category lên Cloudinary: {}", e.getMessage(), e);
                throw new IllegalArgumentException("Không thể upload ảnh lên Cloudinary: " + e.getMessage());
            }
        }

        Category.CategoryBuilder builder = Category.builder()
            .name(request.getName())
            .slug(request.getSlug())
            .description(request.getDescription())
            .image(imageUrl)
            .cloudinaryPublicId(cloudinaryPublicId)
            .order(request.getOrder() != null ? request.getOrder() : 0)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true);

        // Set parent nếu có
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục cha với ID: " + request.getParentId()));
            builder.parent(parent);
        }

        Category category = builder.build();
        category = categoryRepository.save(category);

        return toAdminCategoryResponse(category);
    }

    @Override
    @Transactional
    public AdminCategoryResponse updateCategory(Long id, CategoryUpdateRequest request, MultipartFile imageFile) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục với ID: " + id));

        // Update fields
        if (request.getName() != null) {
            category.setName(request.getName());
        }
        if (request.getSlug() != null && !request.getSlug().equals(category.getSlug())) {
            // Kiểm tra slug trùng (trừ chính nó)
            categoryRepository.findBySlug(request.getSlug())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("Slug đã tồn tại: " + request.getSlug());
                    }
                });
            category.setSlug(request.getSlug());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        // Xử lý ảnh: nếu có file mới thì upload, nếu có URL mới thì dùng URL
        if (imageFile != null && !imageFile.isEmpty()) {
            // Xóa ảnh cũ trên Cloudinary nếu có
            if (category.getCloudinaryPublicId() != null) {
                try {
                    cloudinaryService.deleteImage(category.getCloudinaryPublicId());
                } catch (Exception e) {
                    log.warn("Không thể xóa ảnh Cloudinary cũ {}: {}", category.getCloudinaryPublicId(), e.getMessage());
                }
            }
            // Upload ảnh mới
            try {
                var uploadResult = cloudinaryService.uploadCategoryImage(imageFile);
                category.setImage(uploadResult.secureUrl());
                category.setCloudinaryPublicId(uploadResult.publicId());
            } catch (Exception e) {
                log.error("Lỗi khi upload ảnh category lên Cloudinary: {}", e.getMessage(), e);
                throw new IllegalArgumentException("Không thể upload ảnh lên Cloudinary: " + e.getMessage());
            }
        } else if (request.getImage() != null) {
            // Nếu có URL mới (không phải từ file upload)
            // Nếu URL là base64 data URL, không upload lên Cloudinary
            if (request.getImage().startsWith("data:")) {
                // Base64 - giữ nguyên, không lưu cloudinaryPublicId
                category.setImage(request.getImage());
                // Xóa ảnh cũ trên Cloudinary nếu có
                if (category.getCloudinaryPublicId() != null) {
                    try {
                        cloudinaryService.deleteImage(category.getCloudinaryPublicId());
                    } catch (Exception e) {
                        log.warn("Không thể xóa ảnh Cloudinary cũ {}: {}", category.getCloudinaryPublicId(), e.getMessage());
                    }
                    category.setCloudinaryPublicId(null);
                }
            } else {
                // URL thông thường - giữ nguyên
                category.setImage(request.getImage());
                // Nếu là URL mới (không phải Cloudinary), xóa publicId cũ
                if (category.getCloudinaryPublicId() != null && !request.getImage().contains("cloudinary.com")) {
                    try {
                        cloudinaryService.deleteImage(category.getCloudinaryPublicId());
                    } catch (Exception e) {
                        log.warn("Không thể xóa ảnh Cloudinary cũ {}: {}", category.getCloudinaryPublicId(), e.getMessage());
                    }
                    category.setCloudinaryPublicId(null);
                }
            }
        }

        if (request.getOrder() != null) {
            category.setOrder(request.getOrder());
        }
        if (request.getIsActive() != null) {
            category.setActive(request.getIsActive());
        }

        // Update parent
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new IllegalArgumentException("Danh mục không thể là cha của chính nó");
            }
            Category parent = categoryRepository.findById(request.getParentId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục cha với ID: " + request.getParentId()));
            category.setParent(parent);
        } else if (request.getParentId() == null && category.getParent() != null) {
            // Remove parent nếu parentId = null
            category.setParent(null);
        }

        category = categoryRepository.save(category);
        return toAdminCategoryResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục với ID: " + id));

        // Kiểm tra có sản phẩm trong danh mục không
        long productCount = productRepository.findAll().stream()
            .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(id))
            .count();

        if (productCount > 0) {
            throw new IllegalArgumentException("Không thể xóa danh mục vì có " + productCount + " sản phẩm trong danh mục này.");
        }

        // Kiểm tra có danh mục con không
        if (!category.getChildren().isEmpty()) {
            throw new IllegalArgumentException("Không thể xóa danh mục vì có danh mục con.");
        }

        // Xóa ảnh trên Cloudinary nếu có
        if (category.getCloudinaryPublicId() != null) {
            try {
                cloudinaryService.deleteImage(category.getCloudinaryPublicId());
                log.info("Đã xóa ảnh Cloudinary: {}", category.getCloudinaryPublicId());
            } catch (Exception e) {
                log.warn("Không thể xóa ảnh Cloudinary {}: {}", category.getCloudinaryPublicId(), e.getMessage());
                // Vẫn tiếp tục xóa category dù không xóa được ảnh
            }
        }

        categoryRepository.delete(category);
    }

    @Override
    public String uploadCategoryImage(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("File ảnh không được để trống");
        }
        try {
            var uploadResult = cloudinaryService.uploadCategoryImage(imageFile);
            return uploadResult.secureUrl();
        } catch (Exception e) {
            log.error("Lỗi khi upload ảnh category lên Cloudinary: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Không thể upload ảnh lên Cloudinary: " + e.getMessage());
        }
    }

    private AdminCategoryResponse toAdminCategoryResponse(Category category) {
        // Đếm số sản phẩm trong danh mục
        long productCount = productRepository.findAll().stream()
            .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(category.getId()))
            .count();

        return AdminCategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .slug(category.getSlug())
            .description(category.getDescription())
            .image(category.getImage())
            .parentId(category.getParent() != null ? category.getParent().getId() : null)
            .parentName(category.getParent() != null ? category.getParent().getName() : null)
            .order(category.getOrder())
            .isActive(category.isActive())
            .productCount(productCount)
            .createdAt(category.getCreatedAt())
            .updatedAt(category.getUpdatedAt())
            .build();
    }
}

