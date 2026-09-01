package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.integration.sapo.util.SapoDateTimeParser;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VoucherSyncHealthCheck implements SapoSyncHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(VoucherSyncHealthCheck.class);
    private static final long GRACE_PERIOD_MINUTES = 10;

    private final VoucherRepository voucherRepository;
    private final SapoApiClient sapoApiClient;

    @Override
    public SyncDomain domain() {
        return SyncDomain.VOUCHER;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscrepancyCandidate> checkAll() {
        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        candidates.addAll(checkUnsyncedAndFailed());
        candidates.addAll(checkSyncedForMismatch());
        return candidates;
    }

    private List<DiscrepancyCandidate> checkUnsyncedAndFailed() {
        LocalDateTime graceThreshold = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);
        List<Voucher> unsynced = voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED);
        return unsynced.stream()
                .filter(voucher -> voucher.getSapoSyncStatus() == SapoSyncStatus.FAILED
                        || voucher.getCreatedAt().isBefore(graceThreshold))
                .map(voucher -> new DiscrepancyCandidate(
                        voucher.getId(),
                        voucher.getCode(),
                        voucher.getSapoSyncStatus() == SapoSyncStatus.FAILED
                                ? DiscrepancyType.SYNC_FAILED : DiscrepancyType.NOT_SYNCED,
                        "Voucher sapoSyncStatus=" + voucher.getSapoSyncStatus()))
                .toList();
    }

    private List<DiscrepancyCandidate> checkSyncedForMismatch() {
        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        List<Voucher> synced = voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED);
        for (Voucher voucher : synced) {
            if (voucher.getSapoPriceRuleId() == null) {
                continue;
            }
            try {
                SapoPriceRuleResponse response = sapoApiClient.getPriceRule(voucher.getSapoPriceRuleId());
                if (response == null || response.getPriceRule() == null) {
                    continue;
                }
                String remoteValue = response.getPriceRule().getValue();
                String remoteEndsOn = response.getPriceRule().getEndsOn();

                boolean valueMismatch = checkValueMismatch(voucher.getValue(), remoteValue);
                boolean endsOnMismatch = checkEndsOnMismatch(voucher.getExpiresAt(), remoteEndsOn);

                if (valueMismatch || endsOnMismatch) {
                    candidates.add(new DiscrepancyCandidate(
                            voucher.getId(),
                            voucher.getCode(),
                            DiscrepancyType.VALUE_MISMATCH,
                            "local value=" + voucher.getValue() + " vs sapo value=" + remoteValue
                                    + ", local expiresAt=" + (voucher.getExpiresAt() != null ? voucher.getExpiresAt().toString() : null)
                                    + " vs sapo ends_on=" + remoteEndsOn));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo voucher sync-health check failed for voucher id={}: {}", voucher.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private boolean checkValueMismatch(BigDecimal localValue, String remoteValue) {
        if (localValue == null || remoteValue == null) {
            return localValue != null || remoteValue != null;
        }
        try {
            return localValue.compareTo(new BigDecimal(remoteValue)) != 0;
        } catch (NumberFormatException ex) {
            log.warn("Could not parse remote value '{}' for mismatch check: {}", remoteValue, ex.getMessage());
            return true;
        }
    }

    private boolean checkEndsOnMismatch(LocalDateTime localEndsOn, String remoteEndsOn) {
        if (localEndsOn == null || remoteEndsOn == null) {
            return localEndsOn != null || remoteEndsOn != null;
        }

        try {
            LocalDateTime remoteDateTime = SapoDateTimeParser.parseTolerant(remoteEndsOn);
            LocalDateTime localTruncated = localEndsOn.truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime remoteTruncated = remoteDateTime.truncatedTo(ChronoUnit.MINUTES);
            return !localTruncated.equals(remoteTruncated);
        } catch (DateTimeException ex) {
            log.warn("Could not parse remote ends_on value '{}' for mismatch check: {}", remoteEndsOn, ex.getMessage());
            return true;
        }
    }
}
