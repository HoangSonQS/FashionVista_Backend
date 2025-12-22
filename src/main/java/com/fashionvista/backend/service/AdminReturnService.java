package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ReturnRequestResponse;
import com.fashionvista.backend.dto.UpdateReturnStatusRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminReturnService {

    Page<ReturnRequestResponse> getAll(ReturnStatus status, String search, Pageable pageable);

    ReturnRequestResponse updateStatus(Long id, UpdateReturnStatusRequest request);

    ReturnRequestResponse getByOrder(Long orderId);
}


