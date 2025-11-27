package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserProfileResponse {

    Long id;
    String email;
    String fullName;
    String phoneNumber;
    String role;
    boolean active;
}

