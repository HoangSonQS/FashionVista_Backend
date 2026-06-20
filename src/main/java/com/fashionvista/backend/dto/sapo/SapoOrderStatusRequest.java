package com.fashionvista.backend.dto.sapo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SapoOrderStatusRequest {
    @NotBlank
    private String status;
    private String note;
}
