package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminProductImageResponse;
import com.fashionvista.backend.dto.ReorderImagesRequest;
import com.fashionvista.backend.service.AdminProductImageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductImageController {

    private final AdminProductImageService adminProductImageService;

    @GetMapping
    public List<AdminProductImageResponse> getProductImages(@PathVariable Long productId) {
        return adminProductImageService.getProductImages(productId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public List<AdminProductImageResponse> uploadImages(
        @PathVariable Long productId,
        @RequestPart("images") List<MultipartFile> imageFiles
    ) {
        return adminProductImageService.uploadImages(productId, imageFiles);
    }

    @PatchMapping("/{imageId}/set-primary")
    public AdminProductImageResponse setPrimary(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        return adminProductImageService.setPrimary(productId, imageId);
    }

    @PatchMapping("/reorder")
    public List<AdminProductImageResponse> reorderImages(
        @PathVariable Long productId,
        @RequestBody @Valid ReorderImagesRequest request
    ) {
        return adminProductImageService.reorderImages(productId, request);
    }

    @DeleteMapping("/{imageId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        adminProductImageService.deleteImage(productId, imageId);
    }
}

