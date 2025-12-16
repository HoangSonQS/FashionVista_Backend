package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.CreateReturnRequestRequest;
import com.fashionvista.backend.dto.ReturnRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReturnService {
    ReturnRequestResponse createReturnRequest(CreateReturnRequestRequest request);

    Page<ReturnRequestResponse> getMyReturns(Pageable pageable);
}


