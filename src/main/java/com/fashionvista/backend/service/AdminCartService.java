package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminCartListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminCartService {

    Page<AdminCartListResponse> getAdminCarts(String search, Boolean isAbandoned, Pageable pageable);

    void sendCartReminder(Long cartId);
}
