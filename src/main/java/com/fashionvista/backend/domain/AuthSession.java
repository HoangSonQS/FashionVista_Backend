package com.fashionvista.backend.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {
    private String sessionId;
    private String userId;
    private String refreshTokenHash;
    private String deviceId;
    private String ip;
    private String userAgent;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private boolean revoked;
}
