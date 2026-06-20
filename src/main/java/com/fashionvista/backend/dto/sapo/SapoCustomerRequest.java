package com.fashionvista.backend.dto.sapo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Data;

@Data
public class SapoCustomerRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String fullName;
    @NotBlank
    private String phoneNumber;
    private String gender;
    private LocalDate dateOfBirth;
}
