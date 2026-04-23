package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoucherDiscountResult {
    BigDecimal discount;
    boolean freeShipping;
}
