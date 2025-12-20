package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.VoucherType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Value;

@Value
public class VoucherCreateRequest {
    @NotBlank(message = "Mã voucher không được để trống")
    @Size(max = 64, message = "Mã voucher không được vượt quá 64 ký tự")
    String code;

    @NotNull(message = "Loại voucher không được để trống")
    VoucherType type;

    BigDecimal value; // Required for PERCENT and FIXED_AMOUNT

    Boolean freeShipping;

    BigDecimal minOrderTotal;

    Integer usageLimit;

    Boolean active;

    LocalDateTime startsAt;

    LocalDateTime expiresAt;
}

