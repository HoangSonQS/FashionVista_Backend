package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShippingCreateRequest {
    private String carrier; // GHN / GHTK / JNT
    private String serviceType; // STANDARD / EXPRESS / SAVER
    private Double weight; // gram
    private String note;
}

