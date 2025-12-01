package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.*;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Page<AdminUserListResponse> getAllUsers(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) Boolean active,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            return adminUserService.getAllUsers(search, role, active, pageable);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi lấy danh sách người dùng: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{userId}")
    public AdminUserListResponse getUserById(@PathVariable Long userId) {
        return adminUserService.getUserById(userId);
    }

    @PatchMapping("/{userId}/status")
    public AdminUserListResponse updateUserStatus(
        @PathVariable Long userId,
        @RequestBody @Valid UpdateUserStatusRequest request
    ) {
        return adminUserService.updateUserStatus(userId, request);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserListResponse updateUserRole(
        @PathVariable Long userId,
        @RequestBody @Valid UpdateUserRoleRequest request
    ) {
        return adminUserService.updateUserRole(userId, request);
    }

    @GetMapping("/{userId}/detail")
    public AdminUserDetailResponse getUserDetail(@PathVariable Long userId) {
        return adminUserService.getUserDetail(userId);
    }

    @PostMapping("/{userId}/loyalty-points")
    public AdminUserListResponse addLoyaltyPoints(
        @PathVariable Long userId,
        @RequestBody @Valid AddLoyaltyPointsRequest request
    ) {
        return adminUserService.addLoyaltyPoints(userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
        @PathVariable Long userId,
        @RequestBody @Valid ResetPasswordRequest request
    ) {
        String tempPassword = adminUserService.resetPassword(userId, request);
        return ResponseEntity.ok(
            java.util.Map.of("temporaryPassword", tempPassword, "emailSent", String.valueOf(request.getSendEmail() != null && request.getSendEmail()))
        );
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportUsers(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) Boolean active
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE);
        org.springframework.data.domain.Page<AdminUserListResponse> users = adminUserService.getAllUsers(search, role, active, pageable);
        
        // Tạo CSV content
        StringBuilder csv = new StringBuilder();
        csv.append("Email,Họ tên,Số điện thoại,Vai trò,Trạng thái,Số đơn hàng,Ngày tạo\n");
        
        for (AdminUserListResponse user : users.getContent()) {
            csv.append(escapeCsv(user.getEmail())).append(",");
            csv.append(escapeCsv(user.getFullName() != null ? user.getFullName() : "")).append(",");
            csv.append(escapeCsv(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")).append(",");
            csv.append(escapeCsv(user.getRole().name())).append(",");
            csv.append(escapeCsv(user.isActive() ? "ACTIVE" : "INACTIVE")).append(",");
            csv.append(user.getOrderCount()).append(",");
            csv.append(user.getCreatedAt().toString()).append("\n");
        }
        
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header("Content-Type", "text/csv; charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=users_" + java.time.LocalDate.now() + ".csv")
            .body(bytes);
    }
    
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

