package com.fashionvista.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminProductVariantCreateRequest {
    @NotNull(message = "Product ID không được để trống")
    private Long productId;

    @NotBlank(message = "Size không được để trống")
    private String size;

    @NotBlank(message = "Color không được để trống")
    private String color;

    @NotBlank(message = "SKU không được để trống")
    private String sku;

    @NotNull(message = "Stock không được để trống")
    @Min(value = 0, message = "Stock phải >= 0")
    private Integer stock;

    private BigDecimal price; // Optional, nếu null sẽ dùng giá của product

    private boolean active = true;
}

