package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.UserRole;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminUserListResponse {

    Long id;
    String email;
    String fullName;
    String phoneNumber;
    UserRole role;
    boolean active;
    LocalDateTime createdAt;
    Long orderCount; // Số đơn hàng đã đặt
}

