package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.VoucherType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminVoucherResponse {
    Long id;
    String code;
    VoucherType type;
    BigDecimal value;
    Boolean freeShipping;
    BigDecimal minOrderTotal;
    Integer usageLimit;
    Integer usedCount;
    Boolean active;
    LocalDateTime startsAt;
    LocalDateTime expiresAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

