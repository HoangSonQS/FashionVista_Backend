package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminCategoryResponse;
import com.fashionvista.backend.dto.CategoryCreateRequest;
import com.fashionvista.backend.dto.CategoryUpdateRequest;
import com.fashionvista.backend.service.AdminCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @GetMapping
    public Page<AdminCategoryResponse> getAllCategories(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean isActive,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminCategoryService.getAllCategories(search, isActive, pageable);
    }

    @GetMapping("/{id}")
    public AdminCategoryResponse getCategoryById(@PathVariable Long id) {
        return adminCategoryService.getCategoryById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryResponse createCategory(
        @RequestPart("category") @Valid CategoryCreateRequest request,
        @RequestPart(value = "image", required = false) MultipartFile imageFile
    ) {
        return adminCategoryService.createCategory(request, imageFile);
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminCategoryResponse updateCategory(
        @PathVariable Long id,
        @RequestPart("category") @Valid CategoryUpdateRequest request,
        @RequestPart(value = "image", required = false) MultipartFile imageFile
    ) {
        return adminCategoryService.updateCategory(id, request, imageFile);
    }

    @PostMapping("/upload-image")
    public java.util.Map<String, String> uploadImage(@RequestParam("image") MultipartFile imageFile) {
        String imageUrl = adminCategoryService.uploadCategoryImage(imageFile);
        return java.util.Map.of("url", imageUrl);
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        adminCategoryService.deleteCategory(id);
    }
}

