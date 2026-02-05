package com.fashionvista.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Value;

@Value
public class CategoryUpdateRequest {
    @Size(max = 255, message = "Tên danh mục không được vượt quá 255 ký tự")
    String name;

    @Size(max = 255, message = "Slug không được vượt quá 255 ký tự")
    String slug;

    String description;

    String image;

    Long parentId;

    @jakarta.validation.constraints.Min(value = 0, message = "Thứ tự hiển thị phải >= 0")
    Integer order;

    Boolean isActive;
}

