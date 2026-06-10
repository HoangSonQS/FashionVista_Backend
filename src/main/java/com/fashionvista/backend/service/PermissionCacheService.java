package com.fashionvista.backend.service;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.config.AuthSecurityProperties;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.repository.UserRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthSecurityProperties properties;
    private final UserRepository userRepository;

    public PermissionSnapshot getPermissions(Long userId) {
        String key = key(userId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, PermissionSnapshot.class);
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete(key);
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User khong ton tai."));
        PermissionSnapshot snapshot = fromRole(user.getRole());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), CACHE_TTL);
        } catch (JsonProcessingException ignored) {
            // Do not fail authentication only because cache serialization failed.
        }
        return snapshot;
    }

    public void evictPermissions(Long userId) {
        redisTemplate.delete(key(userId));
    }

    public PermissionSnapshot fromRole(UserRole role) {
        if (role == UserRole.ADMIN) {
            return new PermissionSnapshot(
                    List.of("ADMIN"),
                    List.of("ADMIN_ACCESS", "CATALOG_MANAGE", "ORDER_MANAGE", "USER_MANAGE", "REPORT_READ"),
                    1);
        }
        if (role == UserRole.STAFF) {
            return new PermissionSnapshot(
                    List.of("STAFF"),
                    List.of("STAFF_ACCESS", "CATALOG_READ", "ORDER_MANAGE", "REPORT_READ"),
                    1);
        }
        return new PermissionSnapshot(
                List.of("CUSTOMER"),
                List.of("ORDER_CREATE", "ORDER_READ_SELF", "CART_READ_SELF", "CART_WRITE_SELF"),
                1);
    }

    private String key(Long userId) {
        return properties.getRedis().getKeyPrefix() + "auth:permissions:" + userId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionSnapshot {
        private List<String> roles;
        private List<String> permissions;
        private int version;
    }
}
