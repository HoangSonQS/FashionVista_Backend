package com.fashionvista.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingWebhookPayload {
    private String carrier; // GHN / GHTK / JNT
    private String trackingNumber;
    private String status; // PickedUp / InTransit / Delivered / Return
    private String note;
}

