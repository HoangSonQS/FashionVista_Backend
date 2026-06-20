package com.fashionvista.backend.dto.sapo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoCustomerDto {
    Long id;
    String email;
    String fullName;
    String phoneNumber;
    String gender;
    LocalDate dateOfBirth;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
