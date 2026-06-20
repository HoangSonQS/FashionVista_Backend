package com.fashionvista.backend.dto.sapo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class SapoOrderItemRequest {
    @NotBlank
    private String sku;
    @NotNull
    @Min(1)
    private Integer quantity;
    @NotNull
    private BigDecimal unitPrice;
}
