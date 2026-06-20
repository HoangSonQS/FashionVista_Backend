package com.fashionvista.backend.dto.sapo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoVoucherDto {
    Long id;
    String code;
    String type;
    BigDecimal value;
    boolean freeShipping;
    BigDecimal minOrderTotal;
    Integer usageLimit;
    Integer usedCount;
    boolean active;
    LocalDateTime startsAt;
    LocalDateTime expiresAt;
    boolean available;
    String unavailableReason;
}
