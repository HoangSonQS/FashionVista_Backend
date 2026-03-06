package com.fashionvista.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminProductVariantUpdateRequest {
    @NotBlank(message = "Size không được để trống")
    private String size;

    @NotBlank(message = "Color không được để trống")
    private String color;

    @NotBlank(message = "SKU không được để trống")
    private String sku;

    @Min(value = 0, message = "Stock phải >= 0")
    private Integer stock;

    private BigDecimal price; // Optional

    private BigDecimal compareAtPrice; // Optional

    private Boolean active;
}
