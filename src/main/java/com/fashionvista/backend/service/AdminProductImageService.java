package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminProductImageResponse;
import com.fashionvista.backend.dto.ReorderImagesRequest;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface AdminProductImageService {
    List<AdminProductImageResponse> getProductImages(Long productId);

    List<AdminProductImageResponse> uploadImages(Long productId, List<MultipartFile> imageFiles);

    AdminProductImageResponse setPrimary(Long productId, Long imageId);

    void deleteImage(Long productId, Long imageId);

    List<AdminProductImageResponse> reorderImages(Long productId, ReorderImagesRequest request);
}

