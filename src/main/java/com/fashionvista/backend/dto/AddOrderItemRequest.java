package com.fashionvista.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddOrderItemRequest {
    @NotNull(message = "Product ID không được để trống")
    private Long productId;

    private Long variantId; // Optional: nếu có variant

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity;
}

