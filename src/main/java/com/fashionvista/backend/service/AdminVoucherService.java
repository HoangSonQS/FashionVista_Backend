package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminVoucherResponse;
import com.fashionvista.backend.dto.VoucherCreateRequest;
import com.fashionvista.backend.dto.VoucherUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminVoucherService {
    Page<AdminVoucherResponse> getAllVouchers(String search, Boolean active, Pageable pageable);

    AdminVoucherResponse getVoucherById(Long id);

    AdminVoucherResponse createVoucher(VoucherCreateRequest request);

    AdminVoucherResponse updateVoucher(Long id, VoucherUpdateRequest request);

    void deleteVoucher(Long id);
}

