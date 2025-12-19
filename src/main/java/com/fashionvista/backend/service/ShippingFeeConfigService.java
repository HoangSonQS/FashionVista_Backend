package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ShippingFeeConfigResponse;
import com.fashionvista.backend.dto.ShippingFeeConfigUpdateRequest;
import com.fashionvista.backend.entity.ShippingMethod;
import java.util.List;

public interface ShippingFeeConfigService {
    List<ShippingFeeConfigResponse> getAll();
    ShippingFeeConfigResponse getByMethod(ShippingMethod method);
    ShippingFeeConfigResponse update(Long id, ShippingFeeConfigUpdateRequest request);
}

