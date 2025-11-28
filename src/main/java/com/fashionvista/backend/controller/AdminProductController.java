package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ProductDetailDto;
import com.fashionvista.backend.dto.ProductListResponse;
import com.fashionvista.backend.dto.ProductUpdateRequest;
import com.fashionvista.backend.entity.ProductStatus;
import com.fashionvista.backend.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ProductListResponse getProducts(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) ProductStatus status,
        @RequestParam(required = false) Boolean featured,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return productService.getAdminProducts(category, search, status, featured, page, size);
    }

    @GetMapping("/{id}")
    public ProductDetailDto getDetail(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDetailDto updateProduct(
        @PathVariable Long id,
        @RequestPart("product") @Valid ProductUpdateRequest request,
        @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return productService.updateProduct(id, request, images);
    }

    @PatchMapping("/{id}/status")
    public void updateStatus(
        @PathVariable Long id,
        @RequestBody StatusUpdatePayload payload
    ) {
        productService.updateProductStatus(id, payload.status(), payload.featured());
    }

    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    public record StatusUpdatePayload(ProductStatus status, Boolean featured) {
    }
}



