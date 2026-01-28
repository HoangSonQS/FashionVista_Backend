package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminUserListResponse;
import com.fashionvista.backend.dto.UpdateUserStatusRequest;
import com.fashionvista.backend.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

        Page<AdminUserListResponse> getAllUsers(
                        String search,
                        UserRole role,
                        Boolean active,
                        Pageable pageable);

        AdminUserListResponse getUserById(Long userId);

        AdminUserListResponse updateUserStatus(Long userId, UpdateUserStatusRequest request);

        AdminUserListResponse updateUserRole(Long userId, com.fashionvista.backend.dto.UpdateUserRoleRequest request);

        com.fashionvista.backend.dto.AdminUserDetailResponse getUserDetail(Long userId);

        com.fashionvista.backend.dto.AdminUserListResponse addLoyaltyPoints(Long userId,
                        com.fashionvista.backend.dto.AddLoyaltyPointsRequest request);

        com.fashionvista.backend.dto.AdminUserListResponse createUser(
                        com.fashionvista.backend.dto.CreateUserRequest request);

        String resetPassword(Long userId, com.fashionvista.backend.dto.AdminResetPasswordRequest request);
}
