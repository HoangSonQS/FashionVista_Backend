package com.fashionvista.backend.dto;

import lombok.Data;

@Data
public class ShippingWebhookPayload {
    private String carrier; // GHN / GHTK / JNT
    private String trackingNumber;
    private String status; // PickedUp / InTransit / Delivered / Return
    private String note;
}

