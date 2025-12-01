package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class UpdateUserRoleRequest {

    @NotNull(message = "Vai trò không được để trống")
    UserRole role;
}

