package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.PaymentStatus;
import lombok.Value;

@Value
public class PaymentStatusUpdateRequest {
    PaymentStatus paymentStatus;
}



