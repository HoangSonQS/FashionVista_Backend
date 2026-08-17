package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LinkSapoOrderRequest {
    @NotBlank(message = "Sapo Order ID không được để trống")
    private String sapoOrderId;
}
