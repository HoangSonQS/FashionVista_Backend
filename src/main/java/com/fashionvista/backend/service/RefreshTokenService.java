package com.fashionvista.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.config.AuthSecurityProperties;
import com.fashionvista.backend.domain.AuthSession;
import com.fashionvista.backend.domain.RefreshTokenReuseDetectedException;
import com.fashionvista.backend.entity.User;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthSecurityProperties properties;

    public CreatedSession createSession(User user, HttpServletRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = generateOpaqueToken();
        String refreshTokenHash = sha256(refreshToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getRefreshToken().getTtlSeconds());

        AuthSession session = AuthSession.builder()
                .sessionId(sessionId)
                .userId(String.valueOf(user.getId()))
                .refreshTokenHash(refreshTokenHash)
                .deviceId(UUID.randomUUID().toString())
                .ip(extractClientIp(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        saveSession(session);
        redisTemplate.opsForSet().add(userSessionsKey(user.getId()), sessionId);
        redisTemplate.expire(userSessionsKey(user.getId()), refreshTtl());
        return new CreatedSession(session, refreshToken);
    }

    public RotatedSession rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is missing.");
        }

        String oldHash = sha256(refreshToken);
        String usedValue = redisTemplate.opsForValue().get(usedRefreshKey(oldHash));
        if (usedValue != null) {
            handleReuse(usedValue);
        }

        AuthSession session = findByRefreshHash(oldHash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token khong hop le."));
        if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
            revokeSession(session);
            throw new IllegalArgumentException("Refresh token da het han.");
        }
        if (!oldHash.equals(session.getRefreshTokenHash())) {
            throw new IllegalArgumentException("Refresh token khong hop le.");
        }

        redisTemplate.opsForValue().set(usedRefreshKey(oldHash), session.getSessionId(), refreshTtl());

        String newRefreshToken = generateOpaqueToken();
        session.setRefreshTokenHash(sha256(newRefreshToken));
        session.setLastUsedAt(Instant.now());
        saveSession(session);

        return new RotatedSession(session, newRefreshToken);
    }

    public Optional<AuthSession> findByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return findByRefreshHash(sha256(refreshToken));
    }

    public void revokeSession(AuthSession session) {
        if (session == null || session.getSessionId() == null) {
            return;
        }
        redisTemplate.delete(sessionKey(session.getSessionId()));
        if (session.getUserId() != null) {
            redisTemplate.opsForSet().remove(userSessionsKey(session.getUserId()), session.getSessionId());
        }
    }

    public void revokeAllSessions(Long userId) {
        revokeAllSessions(String.valueOf(userId));
    }

    public void revokeAllSessions(String userId) {
        String userSessionsKey = userSessionsKey(userId);
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        if (sessionIds != null) {
            sessionIds.forEach(sessionId -> redisTemplate.delete(sessionKey(sessionId)));
        }
        redisTemplate.delete(userSessionsKey);
    }

    public boolean sessionExists(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        AuthSession session = readSession(sessionId).orElse(null);
        return session != null && !session.isRevoked() && session.getExpiresAt().isAfter(Instant.now());
    }

    private void handleReuse(String sessionId) {
        readSession(sessionId).ifPresent(session -> revokeAllSessions(session.getUserId()));
        throw new RefreshTokenReuseDetectedException("Refresh token reuse detected.");
    }

    private Optional<AuthSession> findByRefreshHash(String refreshTokenHash) {
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions()
                .match(sessionKey("*"))
                .count(100)
                .build())) {
            while (cursor.hasNext()) {
                Optional<AuthSession> session = readSessionByKey(cursor.next());
                if (session.isPresent() && refreshTokenHash.equals(session.get().getRefreshTokenHash())) {
                    return session;
                }
            }
            return Optional.empty();
        }
    }

    private Optional<AuthSession> readSession(String sessionId) {
        return readSessionByKey(sessionKey(sessionId));
    }

    private Optional<AuthSession> readSessionByKey(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AuthSession.class));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private void saveSession(AuthSession session) {
        try {
            redisTemplate.opsForValue().set(
                    sessionKey(session.getSessionId()),
                    objectMapper.writeValueAsString(session),
                    refreshTtl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Khong the luu phien dang nhap.", ex);
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private Duration refreshTtl() {
        return Duration.ofSeconds(properties.getRefreshToken().getTtlSeconds());
    }

    private String sessionKey(String sessionId) {
        return properties.getRedis().getKeyPrefix() + "auth:session:" + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return userSessionsKey(String.valueOf(userId));
    }

    private String userSessionsKey(String userId) {
        return properties.getRedis().getKeyPrefix() + "auth:user_sessions:" + userId;
    }

    private String usedRefreshKey(String refreshTokenHash) {
        return properties.getRedis().getKeyPrefix() + "auth:used_refresh:" + refreshTokenHash;
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    @AllArgsConstructor
    public static class CreatedSession {
        private AuthSession session;
        private String refreshToken;
    }

    @Data
    @AllArgsConstructor
    public static class RotatedSession {
        private AuthSession session;
        private String refreshToken;
    }
}
