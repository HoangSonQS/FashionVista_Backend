package com.fashionvista.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFeeConfigUpdateRequest {
    @NotNull(message = "Phí cơ bản không được để trống")
    @DecimalMin(value = "0", message = "Phí cơ bản phải >= 0")
    private BigDecimal baseFee;

    @NotNull(message = "Ngưỡng miễn phí không được để trống")
    @DecimalMin(value = "0", message = "Ngưỡng miễn phí phải >= 0")
    private BigDecimal freeShippingThreshold;
}



