package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.ShippingMethod;
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
    private ShippingMethod method;
    private BigDecimal baseFee;
    private BigDecimal freeShippingThreshold;
}



