package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncDiscrepancyRepository extends JpaRepository<SyncDiscrepancy, Long> {

    Optional<SyncDiscrepancy> findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
            SyncDomain domain, Long entityId, DiscrepancyType discrepancyType);

    Page<SyncDiscrepancy> findByDomain(SyncDomain domain, Pageable pageable);

    Page<SyncDiscrepancy> findByDomainAndResolvedAtIsNull(SyncDomain domain, Pageable pageable);

    Page<SyncDiscrepancy> findByDomainAndResolvedAtIsNotNull(SyncDomain domain, Pageable pageable);

    Page<SyncDiscrepancy> findByResolvedAtIsNull(Pageable pageable);

    Page<SyncDiscrepancy> findByResolvedAtIsNotNull(Pageable pageable);
}
