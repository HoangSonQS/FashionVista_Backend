package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class ProductCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private String description;

    private String shortDescription;

    @NotNull
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @NotBlank
    private String sku;

    private ProductStatus status = ProductStatus.ACTIVE;

    private boolean featured = false;

    private String categorySlug;

    private List<String> tags;

    private List<String> sizes;

    private List<String> colors;

    @Valid
    private List<ProductVariantRequest> variants;
}

