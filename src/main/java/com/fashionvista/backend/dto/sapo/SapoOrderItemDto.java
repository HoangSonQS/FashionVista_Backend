package com.fashionvista.backend.dto.sapo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoOrderItemDto {
    String sku;
    String productName;
    String variantLabel;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal subtotal;
}
