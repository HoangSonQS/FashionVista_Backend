package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.repository.SyncDiscrepancyRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncDiscrepancyService {

    private final SyncDiscrepancyRepository syncDiscrepancyRepository;

    @Transactional
    public List<SyncDiscrepancy> reconcile(SyncDomain domain, List<DiscrepancyCandidate> candidates) {
        List<SyncDiscrepancy> newlyDetected = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (DiscrepancyCandidate candidate : candidates) {
            SyncDiscrepancy existing = syncDiscrepancyRepository
                    .findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
                            domain, candidate.entityId(), candidate.discrepancyType())
                    .orElse(null);

            if (existing != null) {
                existing.setLastSeenAt(now);
                existing.setEntityLabel(candidate.entityLabel());
                existing.setDetails(candidate.details());
                syncDiscrepancyRepository.save(existing);
                continue;
            }

            SyncDiscrepancy created = SyncDiscrepancy.builder()
                    .domain(domain)
                    .entityId(candidate.entityId())
                    .entityLabel(candidate.entityLabel())
                    .discrepancyType(candidate.discrepancyType())
                    .details(candidate.details())
                    .detectedAt(now)
                    .lastSeenAt(now)
                    .build();
            newlyDetected.add(syncDiscrepancyRepository.save(created));
        }

        return newlyDetected;
    }

    @Transactional
    public void markAlertSent(List<SyncDiscrepancy> discrepancies) {
        LocalDateTime now = LocalDateTime.now();
        discrepancies.forEach(d -> d.setAlertSentAt(now));
        syncDiscrepancyRepository.saveAll(discrepancies);
    }

    @Transactional(readOnly = true)
    public Page<SyncDiscrepancy> find(SyncDomain domain, Boolean resolved, Pageable pageable) {
        if (domain != null && Boolean.TRUE.equals(resolved)) {
            return syncDiscrepancyRepository.findByDomainAndResolvedAtIsNotNull(domain, pageable);
        }
        if (domain != null && Boolean.FALSE.equals(resolved)) {
            return syncDiscrepancyRepository.findByDomainAndResolvedAtIsNull(domain, pageable);
        }
        if (domain != null) {
            return syncDiscrepancyRepository.findByDomain(domain, pageable);
        }
        if (Boolean.TRUE.equals(resolved)) {
            return syncDiscrepancyRepository.findByResolvedAtIsNotNull(pageable);
        }
        if (Boolean.FALSE.equals(resolved)) {
            return syncDiscrepancyRepository.findByResolvedAtIsNull(pageable);
        }
        return syncDiscrepancyRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public SyncDiscrepancy findByIdOrThrow(Long id) {
        return syncDiscrepancyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy discrepancy."));
    }

    @Transactional
    public void resolve(SyncDiscrepancy discrepancy) {
        discrepancy.setResolvedAt(LocalDateTime.now());
        syncDiscrepancyRepository.save(discrepancy);
    }
}
