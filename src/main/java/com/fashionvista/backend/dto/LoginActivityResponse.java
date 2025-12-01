package com.fashionvista.backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginActivityResponse {

    Long id;
    String ipAddress;
    String userAgent;
    String deviceType;
    String location;
    boolean loginSuccess;
    String failureReason;
    LocalDateTime createdAt;
}

