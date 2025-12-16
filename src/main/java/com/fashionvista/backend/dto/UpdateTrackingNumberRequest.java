package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value
public class UpdateTrackingNumberRequest {

    @NotBlank(message = "Mã vận đơn không được để trống")
    String trackingNumber;

    Boolean notifyCustomer; // Có gửi email thông báo cho khách hàng không
}

