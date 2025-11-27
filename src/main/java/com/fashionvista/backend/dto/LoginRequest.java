package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email hoặc số điện thoại không được để trống")
    private String identifier; // Có thể là email hoặc số điện thoại

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}


