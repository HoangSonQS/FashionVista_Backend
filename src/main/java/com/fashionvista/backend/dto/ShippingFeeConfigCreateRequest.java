package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ShippingFeeConfigCreateRequest {
    @NotBlank
    private String method;

    @NotNull
    private BigDecimal baseFee;

    @NotNull
    private BigDecimal freeShippingThreshold;
}
