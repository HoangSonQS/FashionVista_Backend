package com.fashionvista.backend.dto.sapo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SapoInventoryUpdateRequest {
    @NotNull
    @Min(0)
    private Integer stock;
    private String reason;
    private String referenceId;
}
