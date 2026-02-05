package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.ShippingCreateRequest;
import com.fashionvista.backend.dto.ShippingFeeResponse;
import com.fashionvista.backend.dto.ShippingWebhookPayload;

public interface ShippingService {

    ShippingFeeResponse calculateFee(Long addressId, String service);

    OrderResponse createShipping(String orderNumber, ShippingCreateRequest request);

    OrderResponse cancelShipping(String orderNumber, String note);

    void handleWebhook(ShippingWebhookPayload payload);
}
