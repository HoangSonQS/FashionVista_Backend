package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 0 * * ?") // Chạy mỗi ngày lúc 00:00:00
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired refresh tokens...");
        Instant now = Instant.now();
        refreshTokenRepository.deleteByExpiryDateBefore(now);
        log.info("Finished cleanup of expired refresh tokens.");
    }
}
