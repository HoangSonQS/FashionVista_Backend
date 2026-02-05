package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentStatus;
import java.util.List;
import lombok.Value;

@Value
public class BulkUpdateOrderStatusRequest {
    List<Long> orderIds;
    OrderStatus status;
    PaymentStatus paymentStatus;
    String notes;
    Boolean notifyCustomer;
}


