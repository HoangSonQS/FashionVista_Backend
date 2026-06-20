package com.fashionvista.backend.dto.sapo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SapoVoucherRequest {
    @NotBlank
    private String code;
    @NotNull
    private String type;
    private BigDecimal value;
    private boolean freeShipping;
    private BigDecimal minOrderTotal;
    private Integer usageLimit;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
}
