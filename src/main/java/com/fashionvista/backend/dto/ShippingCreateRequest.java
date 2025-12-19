package com.fashionvista.backend.dto;

import lombok.Data;

@Data
public class ShippingCreateRequest {
    private String carrier; // GHN / GHTK / JNT
    private String serviceType; // STANDARD / EXPRESS / SAVER
    private Double weight; // gram
    private String note;
}

