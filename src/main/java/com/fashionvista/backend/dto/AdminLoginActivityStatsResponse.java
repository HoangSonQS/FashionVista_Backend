package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminLoginActivityStatsResponse {
    Long totalLogins; // Tổng số lần đăng nhập
    Long successfulLogins; // Số lần đăng nhập thành công
    Long failedLogins; // Số lần đăng nhập thất bại
    Long suspiciousActivities; // Số hoạt động đáng ngờ
    Long uniqueUsers; // Số user đã đăng nhập
    Long uniqueIPs; // Số IP khác nhau
}

