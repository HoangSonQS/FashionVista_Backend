package com.fashionvista.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCreateRequest {
    private String carrier; // GHN / GHTK / JNT
    private String serviceType; // STANDARD / EXPRESS / SAVER
    private Double weight; // gram
    private String note;
}

