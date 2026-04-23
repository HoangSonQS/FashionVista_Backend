package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFeeConfigResponse {
    private Long id;
    private String method;
    private BigDecimal baseFee;
    private BigDecimal freeShippingThreshold;
}



