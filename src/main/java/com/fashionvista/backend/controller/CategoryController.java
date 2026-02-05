package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.CategoryResponse;
import com.fashionvista.backend.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
            .filter(category -> category.isActive()) // Chỉ trả về categories đang active
            .map(category -> CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .image(category.getImage())
                .build())
            .toList();
    }
}

