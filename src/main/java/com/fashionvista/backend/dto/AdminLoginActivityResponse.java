package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminLoginActivityResponse {
    Long id;
    Long userId;
    String userEmail;
    String userFullName;
    String ipAddress;
    String userAgent;
    String deviceType;
    String location;
    boolean loginSuccess;
    String failureReason;
    LocalDateTime createdAt;
    boolean suspicious; // IP lạ hoặc nhiều lần thất bại
}

