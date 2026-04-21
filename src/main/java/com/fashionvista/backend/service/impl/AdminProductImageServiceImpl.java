package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminProductImageResponse;
import com.fashionvista.backend.dto.ReorderImagesRequest;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductImage;
import com.fashionvista.backend.repository.ProductImageRepository;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.service.AdminProductImageService;
import com.fashionvista.backend.service.CloudinaryService;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AdminProductImageServiceImpl implements AdminProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public List<AdminProductImageResponse> getProductImages(Long productId) {
        // Optimize: Only query images, don't need product entity
        List<ProductImage> images = productImageRepository.findByProductIdOrderByOrderAsc(productId);
        if (images.isEmpty()) {
            // Only verify product exists if no images (to return proper error)
            productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        }
        return images.stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public List<AdminProductImageResponse> uploadImages(Long productId, List<MultipartFile> imageFiles) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));

        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new IllegalArgumentException("Không có file ảnh nào được upload.");
        }

        List<String> uploadedPublicIds = new ArrayList<>();
        List<ProductImage> newImages = new ArrayList<>();

        try {
            // Get current max order
            List<ProductImage> existingImages = productImageRepository.findByProductIdOrderByOrderAsc(productId);
            int nextOrder = existingImages.isEmpty() ? 0 : existingImages.get(existingImages.size() - 1).getOrder() + 1;
            boolean isFirstImage = existingImages.isEmpty();

            // Validate all files first (fail fast)
            for (MultipartFile imageFile : imageFiles) {
                if (imageFile == null || imageFile.isEmpty()) {
                    continue;
                }
                String contentType = imageFile.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    throw new IllegalArgumentException("File phải là hình ảnh: " + imageFile.getOriginalFilename());
                }
                if (imageFile.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("Kích thước file không được vượt quá 5MB: " + imageFile.getOriginalFilename());
                }
            }

            // Upload all images to Cloudinary in PARALLEL (much faster)
            List<MultipartFile> validFiles = imageFiles.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();

            List<java.util.concurrent.CompletableFuture<CloudinaryService.CloudinaryUploadResult>> uploadFutures = 
                validFiles.stream()
                    .map(file -> java.util.concurrent.CompletableFuture.supplyAsync(() -> 
                        cloudinaryService.uploadImage(file)))
                    .toList();

            // Wait for all uploads to complete
            List<CloudinaryService.CloudinaryUploadResult> uploadResults = uploadFutures.stream()
                .map(java.util.concurrent.CompletableFuture::join)
                .toList();

            // Collect public IDs for cleanup on error
            uploadResults.forEach(result -> uploadedPublicIds.add(result.publicId()));

            // Create all ProductImage entities
            for (int i = 0; i < uploadResults.size(); i++) {
                CloudinaryService.CloudinaryUploadResult uploadResult = uploadResults.get(i);
                ProductImage productImage = ProductImage.builder()
                    .product(product)
                    .url(uploadResult.secureUrl())
                    .cloudinaryPublicId(uploadResult.publicId())
                    .isPrimary(isFirstImage && nextOrder == 0) // Set first image as primary
                    .order(nextOrder++)
                    .build();
                newImages.add(productImage);
                isFirstImage = false;
            }

            // Batch save all images at once (much faster than individual saves)
            if (!newImages.isEmpty()) {
                newImages = productImageRepository.saveAll(newImages);
            }

            return newImages.stream()
                .map(this::toResponse)
                .toList();
        } catch (RuntimeException ex) {
            // Cleanup uploaded images on error
            uploadedPublicIds.forEach(publicId -> {
                try {
                    cloudinaryService.deleteImage(publicId);
                } catch (Exception e) {
                    log.warn("Không thể xóa ảnh Cloudinary {} sau khi thất bại: {}", publicId, e.getMessage());
                }
            });
            throw ex;
        }
    }

    @Override
    @Transactional
    public AdminProductImageResponse setPrimary(Long productId, Long imageId) {
        ProductImage targetImage = productImageRepository.findById(imageId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy ảnh với ID: " + imageId));

        // Verify image belongs to product
        if (!targetImage.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Ảnh không thuộc về sản phẩm này.");
        }
        
        // Verify product exists
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }

        // Optimize: Use bulk update query instead of loading all images
        // Unset all other primary images for this product
        productImageRepository.updatePrimaryStatusForProduct(productId, imageId, false);
        
        // Set target image as primary
        targetImage.setPrimary(true);
        targetImage = productImageRepository.save(targetImage);

        return toResponse(targetImage);
    }

    @Override
    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy ảnh với ID: " + imageId));

        // Verify image belongs to product
        if (!image.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Ảnh không thuộc về sản phẩm này.");
        }

        // Save Cloudinary public ID before deletion (for async cleanup)
        String cloudinaryPublicId = image.getCloudinaryPublicId();
        boolean wasPrimary = image.isPrimary();

        // Remove from collection to prevent CascadeType.ALL from re-saving or conflicting
        image.getProduct().getImages().remove(image);

        // Delete from database FIRST (fast operation, don't block on Cloudinary)
        productImageRepository.delete(image);

        // If deleted image was primary, set first remaining image as primary
        if (wasPrimary) {
            List<ProductImage> remainingImages = productImageRepository.findFirstByProductIdAndIdNotOrderByOrderAsc(productId, imageId);
            if (!remainingImages.isEmpty()) {
                ProductImage firstRemainingImage = remainingImages.get(0);
                firstRemainingImage.setPrimary(true);
                productImageRepository.save(firstRemainingImage);
            }
        }

        // Delete from Cloudinary ASYNC (don't block response)
        if (cloudinaryPublicId != null) {
            deleteCloudinaryImageAsync(cloudinaryPublicId);
        }
    }

    /**
     * Delete image from Cloudinary asynchronously
     * This doesn't block the HTTP response
     */
    private void deleteCloudinaryImageAsync(String publicId) {
        // Use CompletableFuture to run async
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                cloudinaryService.deleteImage(publicId);
                log.debug("Đã xóa ảnh Cloudinary: {}", publicId);
            } catch (Exception ex) {
                log.warn("Không thể xóa ảnh Cloudinary {}: {}", publicId, ex.getMessage());
                // Note: Image already deleted from DB, so this is best-effort cleanup
            }
        });
    }

    @Override
    @Transactional
    public List<AdminProductImageResponse> reorderImages(Long productId, ReorderImagesRequest request) {
        // Verify product exists
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }

        List<Long> imageIds = request.getImageIds();
        if (imageIds == null || imageIds.isEmpty()) {
            throw new IllegalArgumentException("Image IDs không được để trống.");
        }

        // Verify all images belong to product
        List<ProductImage> allImages = productImageRepository.findByProductId(productId);
        List<Long> existingImageIds = allImages.stream().map(ProductImage::getId).toList();
        
        for (Long imageId : imageIds) {
            if (!existingImageIds.contains(imageId)) {
                throw new IllegalArgumentException("Ảnh với ID " + imageId + " không thuộc về sản phẩm này.");
            }
        }

        // Update order based on new order - batch update
        Map<Long, ProductImage> imageMap = allImages.stream()
            .collect(java.util.stream.Collectors.toMap(ProductImage::getId, img -> img));

        List<ProductImage> imagesToUpdate = new ArrayList<>();
        for (int i = 0; i < imageIds.size(); i++) {
            ProductImage image = imageMap.get(imageIds.get(i));
            if (image != null) {
                image.setOrder(i);
                imagesToUpdate.add(image);
            }
        }

        // Batch save all updates at once (much faster than individual saves)
        if (!imagesToUpdate.isEmpty()) {
            productImageRepository.saveAll(imagesToUpdate);
        }

        // Return updated list
        return productImageRepository.findByProductIdOrderByOrderAsc(productId).stream()
            .map(this::toResponse)
            .toList();
    }

    private AdminProductImageResponse toResponse(ProductImage image) {
        return AdminProductImageResponse.builder()
            .id(image.getId())
            .url(image.getUrl())
            .alt(image.getAlt())
            .order(image.getOrder())
            .primary(image.isPrimary())
            .cloudinaryPublicId(image.getCloudinaryPublicId())
            .build();
    }
}

