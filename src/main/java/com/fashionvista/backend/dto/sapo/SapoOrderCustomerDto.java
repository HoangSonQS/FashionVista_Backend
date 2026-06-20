package com.fashionvista.backend.dto.sapo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoOrderCustomerDto {
    Long id;
    String fullName;
    String email;
    String phoneNumber;
}
