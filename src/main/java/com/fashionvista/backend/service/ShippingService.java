package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ShippingFeeResponse;

public interface ShippingService {
    ShippingFeeResponse calculateFee(Long addressId, String service);
}

