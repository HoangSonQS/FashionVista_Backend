package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ShippingFeeConfigCreateRequest;
import com.fashionvista.backend.dto.ShippingFeeConfigResponse;
import com.fashionvista.backend.dto.ShippingFeeConfigUpdateRequest;
import java.util.List;

public interface ShippingFeeConfigService {
    List<ShippingFeeConfigResponse> getAll();
    ShippingFeeConfigResponse getByMethod(String method);
    ShippingFeeConfigResponse create(ShippingFeeConfigCreateRequest request);
    ShippingFeeConfigResponse update(Long id, ShippingFeeConfigUpdateRequest request);
    void delete(Long id);
}



