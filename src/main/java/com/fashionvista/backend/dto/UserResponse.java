package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private boolean active;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .active(user.isActive())
            .build();
    }
}


