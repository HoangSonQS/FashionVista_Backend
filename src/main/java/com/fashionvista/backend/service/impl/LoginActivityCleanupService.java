package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.repository.LoginActivityRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginActivityCleanupService {

    private final LoginActivityRepository loginActivityRepository;

    @Value("${login-activity.retention-days:90}")
    private int retentionDays;

    /**
     * Dọn dẹp log đăng nhập cũ theo chu kỳ.
     * Chạy lúc 03:30 sáng mỗi ngày.
     */
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupOldLoginActivities() {
        if (retentionDays <= 0) {
            log.info("LoginActivity cleanup skipped because retentionDays <= 0");
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        log.info("Cleaning up login activities before {}", threshold);
        loginActivityRepository.deleteByCreatedAtBefore(threshold);
    }
}


