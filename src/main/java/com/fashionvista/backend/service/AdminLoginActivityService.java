package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminLoginActivityResponse;
import com.fashionvista.backend.dto.AdminLoginActivityStatsResponse;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminLoginActivityService {

    /**
     * Lấy danh sách lịch sử đăng nhập với filter.
     */
    Page<AdminLoginActivityResponse> getHistory(
        Long userId,
        Boolean loginSuccess,
        String ipAddress,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Lấy thống kê hoạt động đăng nhập.
     */
    AdminLoginActivityStatsResponse getStats(LocalDateTime startDate, LocalDateTime endDate);
}

