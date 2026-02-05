package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.VoucherType;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Value;

@Value
public class VoucherUpdateRequest {
    @Size(max = 64, message = "Mã voucher không được vượt quá 64 ký tự")
    String code;

    VoucherType type;

    BigDecimal value;

    Boolean freeShipping;

    BigDecimal minOrderTotal;

    Integer usageLimit;

    Boolean active;

    LocalDateTime startsAt;

    LocalDateTime expiresAt;
}

