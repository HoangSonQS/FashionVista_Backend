package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class UpdateOrderStatusRequest {

    @NotNull(message = "Trạng thái đơn hàng không được để trống")
    OrderStatus status;

    PaymentStatus paymentStatus;

    Boolean notifyCustomer;

    String notes; // Ghi chú khi cập nhật trạng thái
}

