package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminCategoryResponse {
    Long id;
    String name;
    String slug;
    String description;
    String image;
    Long parentId;
    String parentName;
    Integer order;
    Boolean isActive;
    Long productCount; // Số sản phẩm trong danh mục
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

