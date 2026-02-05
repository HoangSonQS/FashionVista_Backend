package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdjustLoyaltyPointsRequest;
import com.fashionvista.backend.dto.AdminLoyaltyPointHistoryResponse;
import com.fashionvista.backend.dto.AdminLoyaltyPointStatsResponse;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminLoyaltyPointService {

    /**
     * Lấy danh sách lịch sử điểm thân thiết với filter.
     */
    Page<AdminLoyaltyPointHistoryResponse> getHistory(
        Long userId,
        String transactionType,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Điều chỉnh điểm thủ công (cộng hoặc trừ).
     */
    AdminLoyaltyPointHistoryResponse adjustPoints(AdjustLoyaltyPointsRequest request, Long adminId);

    /**
     * Lấy thống kê tổng điểm theo tier.
     */
    AdminLoyaltyPointStatsResponse getStats();
}

