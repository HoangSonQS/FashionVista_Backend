package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminCategoryResponse;
import com.fashionvista.backend.dto.CategoryCreateRequest;
import com.fashionvista.backend.dto.CategoryUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface AdminCategoryService {
    Page<AdminCategoryResponse> getAllCategories(String search, Boolean isActive, Pageable pageable);

    AdminCategoryResponse getCategoryById(Long id);

    AdminCategoryResponse createCategory(CategoryCreateRequest request, MultipartFile imageFile);

    AdminCategoryResponse updateCategory(Long id, CategoryUpdateRequest request, MultipartFile imageFile);

    void deleteCategory(Long id);

    /**
     * Upload ảnh category lên Cloudinary và trả về URL
     */
    String uploadCategoryImage(MultipartFile imageFile);
}

