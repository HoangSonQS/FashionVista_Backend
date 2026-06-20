package com.fashionvista.backend.dto.sapo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class SapoOrderRequest {
    @NotNull
    private Long customerId;
    @NotEmpty
    @Valid
    private List<SapoOrderItemRequest> items;
    @NotNull
    private String paymentMethod;
    private String voucherCode;
    private BigDecimal shippingFee;
    private String note;
}
