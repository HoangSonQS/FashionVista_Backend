package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.LinkSapoOrderRequest;
import com.fashionvista.backend.dto.SyncDiscrepancyResponse;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.integration.sapo.service.SapoOrderSyncService;
import com.fashionvista.backend.integration.sapo.synchealth.SyncDiscrepancyService;
import com.fashionvista.backend.integration.sapo.synchealth.SyncHealthScheduler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sapo/sync-health")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSyncHealthController {

    private final SyncDiscrepancyService syncDiscrepancyService;
    private final SyncHealthScheduler syncHealthScheduler;
    private final SapoInventorySyncService sapoInventorySyncService;
    private final SapoOrderSyncService sapoOrderSyncService;

    @GetMapping("/discrepancies")
    public ResponseEntity<Page<SyncDiscrepancyResponse>> getDiscrepancies(
            @RequestParam(required = false) SyncDomain domain,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Boolean resolved = status == null ? null : "RESOLVED".equalsIgnoreCase(status);
        Page<SyncDiscrepancy> discrepancies = syncDiscrepancyService.find(domain, resolved, pageable);
        return ResponseEntity.ok(discrepancies.map(this::toResponse));
    }

    @PostMapping("/discrepancies/{id}/push-to-sapo")
    @Transactional
    public ResponseEntity<Void> pushToSapo(@PathVariable Long id) {
        SyncDiscrepancy discrepancy = syncDiscrepancyService.findByIdOrThrow(id);

        if (discrepancy.getDomain() == SyncDomain.INVENTORY) {
            boolean success = sapoInventorySyncService.pushStock(discrepancy.getEntityId());
            if (success) {
                syncDiscrepancyService.resolve(discrepancy);
            }
        } else if (discrepancy.getDomain() == SyncDomain.ORDER) {
            sapoOrderSyncService.pushOrder(discrepancy.getEntityId());
            syncDiscrepancyService.resolve(discrepancy);
        } else {
            throw new IllegalArgumentException("Domain không hỗ trợ push-to-sapo.");
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/discrepancies/{id}/pull-from-sapo")
    @Transactional
    public ResponseEntity<Void> pullFromSapo(@PathVariable Long id) {
        SyncDiscrepancy discrepancy = syncDiscrepancyService.findByIdOrThrow(id);

        if (discrepancy.getDomain() != SyncDomain.INVENTORY) {
            throw new IllegalArgumentException("Chỉ domain INVENTORY hỗ trợ pull-from-sapo.");
        }

        boolean success = sapoInventorySyncService.pullStock(discrepancy.getEntityId());
        if (success) {
            syncDiscrepancyService.resolve(discrepancy);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/discrepancies/{id}/link-sapo-order")
    @Transactional
    public ResponseEntity<Void> linkSapoOrder(@PathVariable Long id, @Valid @RequestBody LinkSapoOrderRequest request) {
        SyncDiscrepancy discrepancy = syncDiscrepancyService.findByIdOrThrow(id);

        if (discrepancy.getDomain() != SyncDomain.ORDER) {
            throw new IllegalArgumentException("Chỉ domain ORDER hỗ trợ link-sapo-order.");
        }

        sapoOrderSyncService.linkSapoOrder(discrepancy.getEntityId(), request.getSapoOrderId());
        syncDiscrepancyService.resolve(discrepancy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @Transactional
    public ResponseEntity<Void> resolve(@PathVariable Long id) {
        SyncDiscrepancy discrepancy = syncDiscrepancyService.findByIdOrThrow(id);
        syncDiscrepancyService.resolve(discrepancy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/run-now")
    public ResponseEntity<Void> runNow() {
        syncHealthScheduler.runNow();
        return ResponseEntity.ok().build();
    }

    private SyncDiscrepancyResponse toResponse(SyncDiscrepancy discrepancy) {
        return SyncDiscrepancyResponse.builder()
                .id(discrepancy.getId())
                .domain(discrepancy.getDomain())
                .entityId(discrepancy.getEntityId())
                .entityLabel(discrepancy.getEntityLabel())
                .discrepancyType(discrepancy.getDiscrepancyType())
                .details(discrepancy.getDetails())
                .detectedAt(discrepancy.getDetectedAt())
                .lastSeenAt(discrepancy.getLastSeenAt())
                .resolvedAt(discrepancy.getResolvedAt())
                .build();
    }
}
