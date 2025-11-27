package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {

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

    private String notes;

    @NotNull
    private PaymentMethod paymentMethod = PaymentMethod.COD;

}

