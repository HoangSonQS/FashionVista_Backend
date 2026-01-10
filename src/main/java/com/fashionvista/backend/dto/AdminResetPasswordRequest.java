package com.fashionvista.backend.dto;

import lombok.Data;

@Data
public class AdminResetPasswordRequest {

    private String newPassword; // Optional. If null/empty -> auto generate
}
