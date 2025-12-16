package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoucherValidationResponse {

    boolean valid;
    String message;
    BigDecimal discount;
    BigDecimal subtotal;
    BigDecimal finalTotal;
}


