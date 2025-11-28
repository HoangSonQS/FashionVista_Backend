package com.fashionvista.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductVariantRequest {

    private Long id;

    private String size;
    private String color;

    @NotBlank
    private String sku;

    @NotNull
    @Min(0)
    private Integer stock;

    private BigDecimal price;

    private boolean active = true;
}

