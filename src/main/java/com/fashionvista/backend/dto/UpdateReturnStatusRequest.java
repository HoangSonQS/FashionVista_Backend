package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.RefundMethod;
import com.fashionvista.backend.entity.ReturnStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateReturnStatusRequest {

    @NotNull
    private ReturnStatus status;

    private String adminNote;

    private RefundMethod refundMethod;

    private BigDecimal refundAmount;
}


