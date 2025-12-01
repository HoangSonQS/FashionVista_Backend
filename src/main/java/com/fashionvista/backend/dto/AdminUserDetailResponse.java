package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.AccountStatus;
import com.fashionvista.backend.entity.CustomerTier;
import com.fashionvista.backend.entity.Gender;
import com.fashionvista.backend.entity.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminUserDetailResponse {

    // Thông tin cơ bản
    Long id;
    String email;
    String fullName;
    String phoneNumber;
    String avatarUrl;
    Gender gender;
    LocalDate dateOfBirth;
    UserRole role;
    AccountStatus status;
    boolean active;
    boolean isEmailVerified;
    String bannedReason;
    LocalDateTime bannedAt;
    LocalDateTime createdAt;

    // Thống kê
    Long totalOrders;
    BigDecimal totalSpent;
    BigDecimal averageOrderValue;
    Integer loyaltyPoints;
    CustomerTier tier;
    LocalDateTime lastLoginAt;
    Long daysSinceLastPurchase;

    // Relationships
    List<AddressResponse> addresses;
    List<OrderSummaryResponse> recentOrders;
    List<WishlistItemResponse> wishlist;
    List<ReviewSummaryResponse> reviews;
    List<LoyaltyPointHistoryResponse> loyaltyHistory;
    List<LoginActivityResponse> loginHistory;
}

