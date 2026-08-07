package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.service.EmailService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyncHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncHealthScheduler.class);

    private final List<SapoSyncHealthCheck> healthChecks;
    private final SyncDiscrepancyService syncDiscrepancyService;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 1800000)
    public void runScheduled() {
        runNow();
    }

    public void runNow() {
        List<SyncDiscrepancy> allNewlyDetected = new ArrayList<>();

        for (SapoSyncHealthCheck check : healthChecks) {
            try {
                List<DiscrepancyCandidate> candidates = check.checkAll();
                List<SyncDiscrepancy> newlyDetected = syncDiscrepancyService.reconcile(check.domain(), candidates);
                allNewlyDetected.addAll(newlyDetected);
            } catch (RuntimeException ex) {
                log.error("Sync health check failed for domain={}: {}", check.domain(), ex.getMessage(), ex);
            }
        }

        if (!allNewlyDetected.isEmpty()) {
            emailService.sendSyncDiscrepancyAlert(allNewlyDetected);
            syncDiscrepancyService.markAlertSent(allNewlyDetected);
        }
    }
}
