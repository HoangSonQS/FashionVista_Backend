package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String phone;

    @NotBlank
    private String address;

    @NotBlank
    private String ward;

    @NotBlank
    private String district;

    @NotBlank
    private String city;

    private boolean isDefault;
}

