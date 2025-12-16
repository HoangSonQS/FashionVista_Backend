package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShippingFeeResponse {
    private BigDecimal fee;
    private String currency;
    private String provider;
    private String service;
    private String note;
}

