package com.fashionvista.backend.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fashionvista.backend.config.AuthSecurityProperties;
import com.fashionvista.backend.domain.LoginRateLimitExceededException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private static final int MAX_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final AuthSecurityProperties properties;

    public void assertAllowed(String identifier, String ip) {
        if (count(emailKey(identifier)) >= MAX_FAILURES || count(ipKey(ip)) >= MAX_IP_FAILURES) {
            throw new LoginRateLimitExceededException("Dang nhap sai qua nhieu lan. Vui long thu lai sau 15 phut.");
        }
    }

    public void recordFailure(String identifier, String ip) {
        increment(emailKey(identifier));
        increment(ipKey(ip));
    }

    public void clearFailures(String identifier, String ip) {
        redisTemplate.delete(emailKey(identifier));
        redisTemplate.delete(ipKey(ip));
    }

    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void increment(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    private String emailKey(String identifier) {
        return properties.getRedis().getKeyPrefix() + "auth:login_fail:" + normalize(identifier);
    }

    private String ipKey(String ip) {
        return properties.getRedis().getKeyPrefix() + "auth:login_fail_ip:" + normalize(ip);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
