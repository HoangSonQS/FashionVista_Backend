# Sapo Sync Health & Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a generic, pluggable reconciliation framework that periodically checks Inventory and Order data for drift between FashionVista's DB and Sapo, alerts admins by email on newly-detected drift, and gives admins a page to manually push/pull/resolve each discrepancy — closing the silent inventory-desync and order-retry-gap issues found in production.

**Architecture:** A new `integration/sapo/synchealth/` package defines a `SapoSyncHealthCheck` interface (one implementation per domain: `InventorySyncHealthCheck`, `OrderSyncHealthCheck`). Spring auto-collects all beans implementing that interface into `SyncHealthScheduler`, which runs every 30 minutes, asks each check for its current discrepancy candidates, and hands them to `SyncDiscrepancyService` for dedup/persistence against a new `sync_discrepancy` table. Newly-detected (never-before-seen) discrepancies trigger one batched admin email. Real-time inventory pushes (`SapoInventorySyncService.pushStock`) are hooked into every code path that mutates variant stock, so the periodic check is a safety net, not the primary sync path. A new `AdminSyncHealthController` + Admin React page let admins list discrepancies and manually push/pull/link/resolve them.

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring Data JPA, Spring Scheduling, Spring Mail (Thymeleaf templates), Maven; React 18 + Vite + TypeScript + Tailwind CSS on the Admin side, Axios for HTTP.

## Global Constraints

- No automatic remediation — every fix requires an explicit admin action (Push, Pull, Link, Resolve).
- Sync health check runs every 30 minutes (`fixedDelay = 1800000` ms).
- One deduplicated admin alert email per newly-detected discrepancy — never re-sent while the discrepancy stays open (`resolved_at IS NULL`).
- The existing hourly `SapoOrderSyncService.retryFailedSyncs()` job is NOT modified — `OrderSyncHealthCheck` is additive.
- Scope is Inventory + Order domains only. Product's own admin retry UI, a unified dashboard, and Customer/Voucher/Shipping/Ledger domains are explicitly out of scope.
- Every 400-response case in `AdminSyncHealthController` MUST throw `IllegalArgumentException` (the codebase's `GlobalExceptionHandler` maps it to 400; see `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\exception\GlobalExceptionHandler.java:57-63`).
- Any new outbound Sapo API call (`getProductVariants`) must follow the existing `SapoApiClient` REST pattern; this plan already models it on Sapo's documented `GET /admin/products/{id}/variants.json` shape per `support.sapo.vn` (per the repo's Sapo Integration Rule in `FashionVista_Backend/CLAUDE.md`).
- No repository-level tests exist anywhere in this Backend codebase (established convention) — Task 1's repository is NOT given a dedicated test file, only a compile check.
- No application-level frontend test files exist anywhere in this Admin codebase (established convention) — Tasks 11/12 (frontend) are verified via `npx tsc --noEmit`, `npm run lint`, and manual dev-server check, not unit tests.
- One commit per repo; do not mix Backend and Admin changes in the same commit.
- Never modify `.env` files. Never push without explicit confirmation.

---

### Task 1: Core entities, enums, and repository

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\entity\SyncDomain.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\entity\DiscrepancyType.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\entity\SyncDiscrepancy.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\repository\SyncDiscrepancyRepository.java`

**Interfaces:**
- Produces: `SyncDomain` enum (`INVENTORY`, `ORDER`), `DiscrepancyType` enum (`NOT_SYNCED`, `VALUE_MISMATCH`, `SYNC_FAILED`), `SyncDiscrepancy` entity with fields `id, domain, entityId, entityLabel, discrepancyType, details, detectedAt, lastSeenAt, resolvedAt, alertSentAt`, and `SyncDiscrepancyRepository` with the query methods below — all consumed by Task 2 onward.

- [ ] **Step 1: Create `SyncDomain` enum**

```java
package com.fashionvista.backend.entity;

public enum SyncDomain {
    INVENTORY,
    ORDER
}
```

- [ ] **Step 2: Create `DiscrepancyType` enum**

```java
package com.fashionvista.backend.entity;

public enum DiscrepancyType {
    NOT_SYNCED,
    VALUE_MISMATCH,
    SYNC_FAILED
}
```

- [ ] **Step 3: Create `SyncDiscrepancy` entity**

```java
package com.fashionvista.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sync_discrepancy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, columnDefinition = "varchar(20) not null")
    private SyncDomain domain;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_label", nullable = false)
    private String entityLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, columnDefinition = "varchar(20) not null")
    private DiscrepancyType discrepancyType;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "alert_sent_at")
    private LocalDateTime alertSentAt;
}
```

No manual migration is needed — `application.properties` uses `ddl-auto=update`, which will create the `sync_discrepancy` table automatically on next boot.

- [ ] **Step 4: Create `SyncDiscrepancyRepository`**

```java
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
```

- [ ] **Step 5: Verify it compiles**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/entity/SyncDomain.java src/main/java/com/fashionvista/backend/entity/DiscrepancyType.java src/main/java/com/fashionvista/backend/entity/SyncDiscrepancy.java src/main/java/com/fashionvista/backend/repository/SyncDiscrepancyRepository.java
git commit -m "feat(sapo): add SyncDiscrepancy entity, enums, and repository"
```

---

### Task 2: `DiscrepancyCandidate`, `SapoSyncHealthCheck` interface, `SyncDiscrepancyService`

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\DiscrepancyCandidate.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\SapoSyncHealthCheck.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\SyncDiscrepancyService.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\synchealth\SyncDiscrepancyServiceTest.java`

**Interfaces:**
- Consumes: `SyncDiscrepancyRepository` (Task 1).
- Produces: `record DiscrepancyCandidate(Long entityId, String entityLabel, DiscrepancyType discrepancyType, String details)`; `interface SapoSyncHealthCheck { SyncDomain domain(); List<DiscrepancyCandidate> checkAll(); }`; `SyncDiscrepancyService` with `List<SyncDiscrepancy> reconcile(SyncDomain, List<DiscrepancyCandidate>)`, `void markAlertSent(List<SyncDiscrepancy>)`, `Page<SyncDiscrepancy> find(SyncDomain, Boolean resolved, Pageable)`, `SyncDiscrepancy findByIdOrThrow(Long)`, `void resolve(SyncDiscrepancy)` — all consumed by Tasks 4, 7, 8, 10.

- [ ] **Step 1: Create `DiscrepancyCandidate` record**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;

public record DiscrepancyCandidate(Long entityId, String entityLabel, DiscrepancyType discrepancyType, String details) {
}
```

- [ ] **Step 2: Create `SapoSyncHealthCheck` interface**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.SyncDomain;
import java.util.List;

public interface SapoSyncHealthCheck {
    SyncDomain domain();
    List<DiscrepancyCandidate> checkAll();
}
```

- [ ] **Step 3: Write the failing test for `SyncDiscrepancyService`**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.repository.SyncDiscrepancyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncDiscrepancyServiceTest {

    @Mock
    private SyncDiscrepancyRepository syncDiscrepancyRepository;

    @InjectMocks
    private SyncDiscrepancyService syncDiscrepancyService;

    @Test
    void reconcile_NewCandidate_InsertsAndReturnsAsNewlyDetected() {
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(1L, "SKU-001", DiscrepancyType.VALUE_MISMATCH, "DB=17, Sapo=20");
        when(syncDiscrepancyRepository.findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
                SyncDomain.INVENTORY, 1L, DiscrepancyType.VALUE_MISMATCH))
                .thenReturn(Optional.empty());
        when(syncDiscrepancyRepository.save(any(SyncDiscrepancy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<SyncDiscrepancy> result = syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of(candidate));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntityId()).isEqualTo(1L);
        assertThat(result.get(0).getResolvedAt()).isNull();
    }

    @Test
    void reconcile_ExistingOpenCandidate_UpdatesLastSeenAtAndDoesNotReturnAsNew() {
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(1L, "SKU-001", DiscrepancyType.VALUE_MISMATCH, "DB=17, Sapo=20");
        SyncDiscrepancy existing = SyncDiscrepancy.builder()
                .id(99L)
                .domain(SyncDomain.INVENTORY)
                .entityId(1L)
                .entityLabel("SKU-001")
                .discrepancyType(DiscrepancyType.VALUE_MISMATCH)
                .build();
        when(syncDiscrepancyRepository.findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
                SyncDomain.INVENTORY, 1L, DiscrepancyType.VALUE_MISMATCH))
                .thenReturn(Optional.of(existing));

        List<SyncDiscrepancy> result = syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of(candidate));

        assertThat(result).isEmpty();
        verify(syncDiscrepancyRepository, times(1)).save(existing);
        verify(syncDiscrepancyRepository, never()).save(any(SyncDiscrepancy.class));
    }

    @Test
    void resolve_SetsResolvedAtAndSaves() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder().id(1L).build();

        syncDiscrepancyService.resolve(discrepancy);

        assertThat(discrepancy.getResolvedAt()).isNotNull();
        verify(syncDiscrepancyRepository).save(discrepancy);
    }

    @Test
    void findByIdOrThrow_NotFound_ThrowsIllegalArgumentException() {
        when(syncDiscrepancyRepository.findById(404L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> syncDiscrepancyService.findByIdOrThrow(404L));
    }
}
```

Note: the second test above (`reconcile_ExistingOpenCandidate...`) has a `verify(..., never()).save(any(SyncDiscrepancy.class))` line that will fail to distinguish call args correctly against the `times(1)).save(existing)` line — Mockito's `never()` with a generic matcher after a specific `times(1)` call on the same mock method is redundant but not contradictory (it does not assert zero calls total, only zero further ones matching `any()`, which will actually fail since `existing` also matches `any()`). Remove that redundant line so the test asserts exactly one save call:

```java
    @Test
    void reconcile_ExistingOpenCandidate_UpdatesLastSeenAtAndDoesNotReturnAsNew() {
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(1L, "SKU-001", DiscrepancyType.VALUE_MISMATCH, "DB=17, Sapo=20");
        SyncDiscrepancy existing = SyncDiscrepancy.builder()
                .id(99L)
                .domain(SyncDomain.INVENTORY)
                .entityId(1L)
                .entityLabel("SKU-001")
                .discrepancyType(DiscrepancyType.VALUE_MISMATCH)
                .build();
        when(syncDiscrepancyRepository.findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
                SyncDomain.INVENTORY, 1L, DiscrepancyType.VALUE_MISMATCH))
                .thenReturn(Optional.of(existing));

        List<SyncDiscrepancy> result = syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of(candidate));

        assertThat(result).isEmpty();
        verify(syncDiscrepancyRepository, times(1)).save(existing);
    }
```

Use this corrected version (drop the `never()` import usage accordingly — remove the unused `never` static import).

- [ ] **Step 4: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SyncDiscrepancyServiceTest`
Expected: FAIL — `SyncDiscrepancyService` class not defined yet.

- [ ] **Step 5: Implement `SyncDiscrepancyService`**

```java
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
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SyncDiscrepancyServiceTest`
Expected: PASS (4/4)

- [ ] **Step 7: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/synchealth/DiscrepancyCandidate.java src/main/java/com/fashionvista/backend/integration/sapo/synchealth/SapoSyncHealthCheck.java src/main/java/com/fashionvista/backend/integration/sapo/synchealth/SyncDiscrepancyService.java src/test/java/com/fashionvista/backend/integration/sapo/synchealth/SyncDiscrepancyServiceTest.java
git commit -m "feat(sapo): add SapoSyncHealthCheck interface and SyncDiscrepancyService"
```

---

### Task 3: Admin alert email — config, `EmailService`/`EmailServiceImpl`, Thymeleaf template

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\resources\application.properties` (after line 90)
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\service\EmailService.java` (insert after line 56, before closing brace at line 57)
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\service\impl\EmailServiceImpl.java` (new `@Value` fields after line 60; new methods appended before closing brace at line 383)
- Create: `D:\FashionVista\FashionVista_Backend\src\main\resources\templates\email\sync-discrepancy-alert.html`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\service\impl\EmailServiceImplTest.java`

**Interfaces:**
- Consumes: `SyncDiscrepancy` entity (Task 1).
- Produces: `EmailService.sendSyncDiscrepancyAlert(List<SyncDiscrepancy> newlyDetected)`, consumed by Task 4's `SyncHealthScheduler`.

- [ ] **Step 1: Add config properties**

Append to `application.properties` after line 90 (end of the "Sapo Outbound Integration" section):

```properties

# Sync Health Configuration
admin.alert.email=${ADMIN_ALERT_EMAIL:}
app.admin.url=${ADMIN_URL:http://localhost:5174}
```

- [ ] **Step 2: Add the interface method to `EmailService.java`**

Insert after line 56 (the `sendAbandonedCartEmail` declaration), before the closing brace at line 57:

```java
    /**
     * Gửi email cảnh báo lệch đồng bộ Sapo cho admin
     *
     * @param newlyDetected Danh sách discrepancy mới phát hiện trong chu kỳ kiểm tra này
     */
    void sendSyncDiscrepancyAlert(java.util.List<com.fashionvista.backend.entity.SyncDiscrepancy> newlyDetected);
```

(Fully-qualified types are used here to match this file's existing style at line 56, which fully-qualifies `com.fashionvista.backend.entity.Cart` rather than adding an import.)

- [ ] **Step 3: Create the Thymeleaf template**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cảnh báo lệch đồng bộ Sapo</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
        }
        .container {
            background-color: #f9f9f9;
            padding: 30px;
            border-radius: 10px;
        }
        .header {
            text-align: center;
            margin-bottom: 30px;
        }
        .logo {
            font-size: 24px;
            font-weight: bold;
            color: #4a90e2;
        }
        .content {
            background-color: white;
            padding: 30px;
            border-radius: 5px;
            margin-bottom: 20px;
        }
        .discrepancy-item {
            background-color: #fdf2f2;
            padding: 15px;
            border-left: 4px solid #e24a4a;
            margin: 15px 0;
        }
        .button {
            display: inline-block;
            padding: 12px 30px;
            background-color: #4a90e2;
            color: white;
            text-decoration: none;
            border-radius: 5px;
            margin: 20px 0;
        }
        .footer {
            text-align: center;
            color: #666;
            font-size: 12px;
            margin-top: 20px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo" th:text="${appName}">FashionVista</div>
        </div>
        <div class="content">
            <h2>Phát hiện lệch đồng bộ Sapo</h2>
            <p>Hệ thống vừa phát hiện các lệch đồng bộ mới giữa FashionVista và Sapo:</p>
            <div th:each="discrepancy : ${discrepancies}" class="discrepancy-item">
                <p><strong>Domain:</strong> <span th:text="${discrepancy.domain}">INVENTORY</span></p>
                <p><strong>Đối tượng:</strong> <span th:text="${discrepancy.entityLabel}">SKU-001</span></p>
                <p><strong>Loại lệch:</strong> <span th:text="${discrepancy.discrepancyType}">VALUE_MISMATCH</span></p>
                <p><strong>Chi tiết:</strong> <span th:text="${discrepancy.details}">DB stock=17, Sapo stock=20</span></p>
            </div>
            <div style="text-align: center;">
                <a th:href="${adminLink}" class="button">Xử lý ngay</a>
            </div>
        </div>
        <div class="footer">
            <p>Email này được gửi tự động, vui lòng không trả lời.</p>
            <p>&copy; 2026 <span th:text="${appName}">FashionVista</span>. Tất cả quyền được bảo lưu.</p>
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 4: Write the failing test for `EmailServiceImpl.sendSyncDiscrepancyAlert`**

```java
package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private SpringTemplateEngine emailTemplateEngine;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailTemplateEngine = mock(SpringTemplateEngine.class);
        emailService = new EmailServiceImpl(mailSender, emailTemplateEngine,
                mock(com.fashionvista.backend.repository.UserRepository.class),
                mock(com.fashionvista.backend.repository.OrderRepository.class));
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@fashionvista.test");
        ReflectionTestUtils.setField(emailService, "fromName", "FashionVista");
        ReflectionTestUtils.setField(emailService, "appName", "FashionVista");
        ReflectionTestUtils.setField(emailService, "adminAlertEmail", "admin@fashionvista.test");
        ReflectionTestUtils.setField(emailService, "adminUrl", "http://localhost:5174");
    }

    @Test
    void sendSyncDiscrepancyAlert_AdminEmailConfigured_SendsOneEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(emailTemplateEngine.process(org.mockito.ArgumentMatchers.eq("sync-discrepancy-alert"), any()))
                .thenReturn("<html></html>");

        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .domain(SyncDomain.INVENTORY)
                .entityId(1L)
                .entityLabel("SKU-001")
                .discrepancyType(DiscrepancyType.VALUE_MISMATCH)
                .details("DB=17, Sapo=20")
                .build();

        emailService.sendSyncDiscrepancyAlert(List.of(discrepancy));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendSyncDiscrepancyAlert_AdminEmailBlank_DoesNotSend() {
        ReflectionTestUtils.setField(emailService, "adminAlertEmail", "");

        emailService.sendSyncDiscrepancyAlert(List.of());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
```

**Note on the constructor call above:** `EmailServiceImpl`'s manual constructor (lines 39-48 of the existing file) has exactly 4 final fields: `mailSender`, `emailTemplateEngine`, and two repositories used by other methods (`UserRepository`, `OrderRepository` per the file's existing imports and method bodies referencing users/orders elsewhere in the class). Confirm the exact field order in the real constructor before writing this test — if a repository has a different type/order than shown above, adjust the mock arguments to match the constructor's real signature (do not change the constructor itself; this task never modifies field order, only appends two new `@Value` fields and two new methods).

- [ ] **Step 5: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=EmailServiceImplTest`
Expected: FAIL — `sendSyncDiscrepancyAlert` not defined on `EmailServiceImpl`, and `adminAlertEmail`/`adminUrl` fields don't exist yet.

- [ ] **Step 6: Add `@Value` fields to `EmailServiceImpl.java`**

Insert after line 60 (after the existing `appName` `@Value` field):

```java
    @Value("${admin.alert.email:}")
    private String adminAlertEmail;

    @Value("${app.admin.url:http://localhost:5174}")
    private String adminUrl;
```

- [ ] **Step 7: Implement `sendSyncDiscrepancyAlert` and its HTML builder**

Append before the closing brace at line 383, following the exact pattern of the existing `sendAbandonedCartEmail`/`buildAbandonedCartHtml` pair (manual `MimeMessageHelper` construction, `isSmtpConfigured()` guard, try/catch that logs and never throws):

```java
    @Override
    public void sendSyncDiscrepancyAlert(java.util.List<com.fashionvista.backend.entity.SyncDiscrepancy> newlyDetected) {
        if (!isSmtpConfigured() || !StringUtils.hasText(adminAlertEmail)) {
            log.warn("Bỏ qua gửi email cảnh báo lệch đồng bộ Sapo vì SMTP hoặc admin.alert.email chưa cấu hình.");
            return;
        }
        try {
            String htmlContent = buildSyncDiscrepancyAlertHtml(newlyDetected);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, fromName);
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(adminAlertEmail);
            helper.setSubject("[" + appName + "] Phát hiện " + newlyDetected.size() + " lệch đồng bộ Sapo");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email cảnh báo lệch đồng bộ Sapo đến: {}", adminAlertEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cảnh báo lệch đồng bộ Sapo đến: {}", adminAlertEmail, e);
        }
    }

    private String buildSyncDiscrepancyAlertHtml(java.util.List<com.fashionvista.backend.entity.SyncDiscrepancy> discrepancies) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("discrepancies", discrepancies);
        context.setVariable("adminLink", adminUrl + "/sync-health");
        context.setVariable("appName", appName);
        return emailTemplateEngine.process("sync-discrepancy-alert", context);
    }
```

This relies on `log`, `mailSender`, `emailTemplateEngine`, `fromEmail`, `fromName`, `appName`, `isSmtpConfigured()`, `StringUtils`, `MimeMessage`, `MimeMessageHelper`, `UnsupportedEncodingException`, `Context`, and `Locale` all already being available in this file (confirmed present, used identically by `sendAbandonedCartEmail`/`buildAbandonedCartHtml`). No new imports are required — fully-qualified `SyncDiscrepancy`/`List` avoid touching the existing import block.

- [ ] **Step 8: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=EmailServiceImplTest`
Expected: PASS (2/2)

- [ ] **Step 9: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/resources/application.properties src/main/java/com/fashionvista/backend/service/EmailService.java src/main/java/com/fashionvista/backend/service/impl/EmailServiceImpl.java src/main/resources/templates/email/sync-discrepancy-alert.html src/test/java/com/fashionvista/backend/service/impl/EmailServiceImplTest.java
git commit -m "feat(sapo): add sync discrepancy admin alert email"
```

---

### Task 4: `SyncHealthScheduler`

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\SyncHealthScheduler.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\synchealth\SyncHealthSchedulerTest.java`

**Interfaces:**
- Consumes: `List<SapoSyncHealthCheck>` (Spring auto-collects all `@Component` beans implementing the interface — Tasks 7 & 8 add to this list without modifying this file), `SyncDiscrepancyService.reconcile`/`markAlertSent` (Task 2), `EmailService.sendSyncDiscrepancyAlert` (Task 3).
- Produces: `public void runNow()` — consumed by Task 10's `AdminSyncHealthController` "run now" endpoint.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.service.EmailService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncHealthSchedulerTest {

    @Test
    void runNow_OneCheckThrows_OtherChecksStillRunAndEmailSentForSurvivors() {
        SapoSyncHealthCheck failingCheck = mock(SapoSyncHealthCheck.class);
        when(failingCheck.domain()).thenReturn(SyncDomain.INVENTORY);
        when(failingCheck.checkAll()).thenThrow(new RuntimeException("Sapo unreachable"));

        SapoSyncHealthCheck workingCheck = mock(SapoSyncHealthCheck.class);
        when(workingCheck.domain()).thenReturn(SyncDomain.ORDER);
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(5L, "ORD-0005", DiscrepancyType.NOT_SYNCED, "status=CONFIRMED");
        when(workingCheck.checkAll()).thenReturn(List.of(candidate));

        SyncDiscrepancyService syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        SyncDiscrepancy newlyDetected = SyncDiscrepancy.builder().id(1L).domain(SyncDomain.ORDER).entityId(5L).build();
        when(syncDiscrepancyService.reconcile(SyncDomain.ORDER, List.of(candidate)))
                .thenReturn(List.of(newlyDetected));

        EmailService emailService = mock(EmailService.class);

        SyncHealthScheduler scheduler = new SyncHealthScheduler(
                List.of(failingCheck, workingCheck), syncDiscrepancyService, emailService);

        scheduler.runNow();

        verify(workingCheck, times(1)).checkAll();
        verify(failingCheck, times(1)).checkAll();
        verify(emailService, times(1)).sendSyncDiscrepancyAlert(List.of(newlyDetected));
        verify(syncDiscrepancyService, times(1)).markAlertSent(List.of(newlyDetected));
    }

    @Test
    void runNow_NoNewDiscrepancies_NoEmailSent() {
        SapoSyncHealthCheck check = mock(SapoSyncHealthCheck.class);
        when(check.domain()).thenReturn(SyncDomain.INVENTORY);
        when(check.checkAll()).thenReturn(List.of());

        SyncDiscrepancyService syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        when(syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of())).thenReturn(List.of());

        EmailService emailService = mock(EmailService.class);

        SyncHealthScheduler scheduler = new SyncHealthScheduler(List.of(check), syncDiscrepancyService, emailService);

        scheduler.runNow();

        verify(emailService, never()).sendSyncDiscrepancyAlert(anyList());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SyncHealthSchedulerTest`
Expected: FAIL — `SyncHealthScheduler` class not defined yet.

- [ ] **Step 3: Implement `SyncHealthScheduler`**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SyncHealthSchedulerTest`
Expected: PASS (2/2)

- [ ] **Step 5: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/synchealth/SyncHealthScheduler.java src/test/java/com/fashionvista/backend/integration/sapo/synchealth/SyncHealthSchedulerTest.java
git commit -m "feat(sapo): add SyncHealthScheduler running every 30 minutes"
```

---

### Task 5: `SapoApiClient.getProductVariants`, `SapoProductVariantsResponse`, `SapoInventorySyncService`

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\client\SapoApiClient.java` (insert after line 71, before class closing brace at line 72)
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoProductVariantsResponse.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\service\SapoInventorySyncService.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\service\SapoInventorySyncServiceTest.java`

**Interfaces:**
- Consumes: `SapoApiClient.updateProduct(String, SapoProductPushRequest)` (existing), `ProductVariantRepository.findById` (existing).
- Produces: `SapoApiClient.getProductVariants(String sapoProductId): SapoProductVariantsResponse`; `SapoInventorySyncService.pushStock(Long variantId): boolean` and `pullStock(Long variantId): boolean` — consumed by Task 6 (hooks), Task 7 (`InventorySyncHealthCheck`), and Task 10 (`AdminSyncHealthController`).

Sapo's real Admin API exposes variant listing at `GET /admin/products/{id}/variants.json` returning `{"variants": [...]}` with each variant carrying `inventory_quantity` (per `support.sapo.vn`, consistent with the outbound `inventory_quantity` field already used in `SapoProductPushRequest.Variant`). `SapoInventorySyncService` cannot send a single-variant patch — `SapoProductSyncService.buildRequest()` proves Sapo's update endpoint expects the full product-with-all-variants payload (`product.getVariants().stream().map(this::toVariant)`) — so `SapoInventorySyncService` builds its own full-product payload (mirroring, not reusing, `SapoProductSyncService`'s private `buildRequest`/`toVariant`) and returns `boolean` success/failure without touching `Product`'s own `sapoSyncStatus`/`sapoSyncError`/`sapoSyncedAt` fields, since those belong to the Product domain, not the Inventory domain tracked by `SyncDiscrepancy`.

- [ ] **Step 1: Add `getProductVariants` to `SapoApiClient.java`**

Insert after line 71 (end of `createOrder`), before the class's closing brace at line 72:

```java

    public SapoProductVariantsResponse getProductVariants(String sapoProductId) {
        return restClient.get()
                .uri("/admin/products/{id}/variants.json", sapoProductId)
                .retrieve()
                .body(SapoProductVariantsResponse.class);
    }
```

Add the import `import com.fashionvista.backend.integration.sapo.dto.SapoProductVariantsResponse;` alongside the file's existing `integration.sapo.dto` imports.

- [ ] **Step 2: Create `SapoProductVariantsResponse` DTO**

Modeled on the existing `SapoProductPushResponse.java` pattern (`@Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)` with nested static classes):

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoProductVariantsResponse {

    private List<Variant> variants;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Variant {
        private String id;
        private String sku;

        @JsonProperty("inventory_quantity")
        private Integer inventoryQuantity;
    }
}
```

- [ ] **Step 3: Write the failing test for `SapoInventorySyncService`**

```java
package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductVariantsResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoInventorySyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private SapoInventorySyncService sapoInventorySyncService;

    private ProductVariant variantWithSapoLinks() {
        Product product = Product.builder()
                .id(1L)
                .name("Áo thun")
                .price(BigDecimal.valueOf(100000))
                .sapoProductId("sapo-prod-1")
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L)
                .product(product)
                .sku("SKU-001")
                .size("M")
                .color("Trắng")
                .price(BigDecimal.valueOf(100000))
                .stock(17)
                .sapoVariantId("sapo-variant-10")
                .build();
        product.setVariants(List.of(variant));
        return variant;
    }

    @Test
    void pushStock_VariantLinkedToSapo_CallsUpdateProductAndReturnsTrue() {
        ProductVariant variant = variantWithSapoLinks();
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        boolean result = sapoInventorySyncService.pushStock(10L);

        assertThat(result).isTrue();
        verify(sapoApiClient).updateProduct(eq("sapo-prod-1"), any(SapoProductPushRequest.class));
    }

    @Test
    void pushStock_VariantHasNoSapoVariantId_ReturnsFalseWithoutCallingSapo() {
        ProductVariant variant = variantWithSapoLinks();
        variant.setSapoVariantId(null);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        boolean result = sapoInventorySyncService.pushStock(10L);

        assertThat(result).isFalse();
        verify(sapoApiClient, never()).updateProduct(any(), any());
    }

    @Test
    void pushStock_SapoApiThrows_ReturnsFalseAndDoesNotThrow() {
        ProductVariant variant = variantWithSapoLinks();
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(sapoApiClient.updateProduct(any(), any())).thenThrow(new RuntimeException("Sapo down"));

        boolean result = sapoInventorySyncService.pushStock(10L);

        assertThat(result).isFalse();
    }

    @Test
    void pullStock_MatchingRemoteVariant_OverwritesLocalStockAndReturnsTrue() {
        ProductVariant variant = variantWithSapoLinks();
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        SapoProductVariantsResponse.Variant remoteVariant = new SapoProductVariantsResponse.Variant();
        remoteVariant.setId("sapo-variant-10");
        remoteVariant.setSku("SKU-001");
        remoteVariant.setInventoryQuantity(20);
        SapoProductVariantsResponse response = new SapoProductVariantsResponse();
        response.setVariants(List.of(remoteVariant));
        when(sapoApiClient.getProductVariants("sapo-prod-1")).thenReturn(response);

        boolean result = sapoInventorySyncService.pullStock(10L);

        assertThat(result).isTrue();
        assertThat(variant.getStock()).isEqualTo(20);
        verify(productVariantRepository).save(variant);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SapoInventorySyncServiceTest`
Expected: FAIL — `SapoInventorySyncService` class not defined yet.

- [ ] **Step 5: Implement `SapoInventorySyncService`**

```java
package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductVariantsResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoInventorySyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoInventorySyncService.class);
    private static final String INVENTORY_MANAGEMENT_BIZWEB = "bizweb";

    private final SapoApiClient sapoApiClient;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    public boolean pushStock(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId).orElse(null);
        if (variant == null || variant.getSapoVariantId() == null
                || variant.getProduct() == null || variant.getProduct().getSapoProductId() == null) {
            return false;
        }

        try {
            SapoProductPushRequest request = buildRequest(variant.getProduct());
            sapoApiClient.updateProduct(variant.getProduct().getSapoProductId(), request);
            return true;
        } catch (RuntimeException ex) {
            log.error("Sapo inventory push failed for variant id={}: {}", variantId, ex.getMessage(), ex);
            return false;
        }
    }

    @Transactional
    public boolean pullStock(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId).orElse(null);
        if (variant == null || variant.getSapoVariantId() == null
                || variant.getProduct() == null || variant.getProduct().getSapoProductId() == null) {
            return false;
        }

        try {
            SapoProductVariantsResponse response = sapoApiClient.getProductVariants(
                    variant.getProduct().getSapoProductId());
            if (response == null || response.getVariants() == null) {
                return false;
            }
            for (SapoProductVariantsResponse.Variant remote : response.getVariants()) {
                if (variant.getSapoVariantId().equals(remote.getId())) {
                    variant.setStock(remote.getInventoryQuantity() != null ? remote.getInventoryQuantity() : 0);
                    productVariantRepository.save(variant);
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            log.error("Sapo inventory pull failed for variant id={}: {}", variantId, ex.getMessage(), ex);
            return false;
        }
    }

    private SapoProductPushRequest buildRequest(Product product) {
        List<SapoProductPushRequest.Variant> variants = product.getVariants().stream()
                .map(this::toVariant)
                .toList();

        SapoProductPushRequest.Product productPayload = SapoProductPushRequest.Product.builder()
                .name(product.getName())
                .variants(variants)
                .build();

        return SapoProductPushRequest.builder()
                .product(productPayload)
                .build();
    }

    private SapoProductPushRequest.Variant toVariant(ProductVariant variant) {
        BigDecimal effectivePrice = (variant.getPrice() != null && variant.getPrice().compareTo(BigDecimal.ZERO) > 0)
                ? variant.getPrice()
                : variant.getProduct().getPrice();

        return SapoProductPushRequest.Variant.builder()
                .id(variant.getSapoVariantId())
                .option1(variant.getSize())
                .option2(variant.getColor())
                .price(effectivePrice != null ? effectivePrice.toPlainString() : null)
                .sku(variant.getSku())
                .inventoryManagement(INVENTORY_MANAGEMENT_BIZWEB)
                .inventoryQuantity(variant.getStock())
                .build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SapoInventorySyncServiceTest`
Expected: PASS (4/4)

- [ ] **Step 7: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductVariantsResponse.java src/main/java/com/fashionvista/backend/integration/sapo/service/SapoInventorySyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoInventorySyncServiceTest.java
git commit -m "feat(sapo): add SapoInventorySyncService for real-time stock push/pull"
```

---

### Task 6: Hook real-time inventory push into every stock-mutating code path

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\service\impl\OrderServiceImpl.java` (field after line 52; hooks in `decreaseStockForOrder`, `decreaseStock`, `restockItems`)
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\service\impl\AdminOrderServiceImpl.java` (field after line 67; hook in `addOrderItem`)
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\service\impl\AdminReturnServiceImpl.java` (field after line 30; hook in `restockReturnItemsIfNeeded`)
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\service\impl\OrderServiceImplTest.java` (new)
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\service\impl\AdminReturnServiceImplTest.java` (new)

**Interfaces:**
- Consumes: `SapoInventorySyncService.pushStock(Long variantId): boolean` (Task 5).

- [ ] **Step 1: Add the field + import to `OrderServiceImpl.java`**

Add `import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;` to the import block, and insert this field after line 52 (the last existing field, `private final VnPayService vnPayService;`):

```java
    private final SapoInventorySyncService sapoInventorySyncService;
```

`OrderServiceImpl` uses `@RequiredArgsConstructor` (confirmed at line 39), so Lombok regenerates the constructor automatically — no manual constructor edit needed.

- [ ] **Step 2: Hook `decreaseStockForOrder`**

The existing `forEach` loop (originally lines 282-294) becomes:

```java
        order.getItems().forEach(orderItem -> {
            if (orderItem.getVariant() != null) {
                int affected = productVariantRepository.decreaseStockIfEnough(
                        orderItem.getVariant().getId(),
                        orderItem.getQuantity());
                if (affected == 0) {
                    log.warn("Không thể decrease stock cho variant {} trong order {}",
                            orderItem.getVariant().getId(), order.getOrderNumber());
                } else {
                    sapoInventorySyncService.pushStock(orderItem.getVariant().getId());
                }
            }
        });
```

(Keep the exact original `log.warn(...)` message text from the current file — only the `else` branch calling `sapoInventorySyncService.pushStock(...)` is new.)

- [ ] **Step 3: Hook `decreaseStock`**

The existing private method (originally lines 317-328) becomes:

```java
    private void decreaseStock(CartItem item) {
        int affected = productVariantRepository.decreaseStockIfEnough(
                item.getVariant().getId(),
                item.getQuantity());

        if (affected == 0) {
            throw new IllegalArgumentException("Sản phẩm " + item.getVariant().getSku() + " không đủ tồn kho.");
        }
        sapoInventorySyncService.pushStock(item.getVariant().getId());
    }
```

(Keep the original exception message text from the current file — only the final `sapoInventorySyncService.pushStock(...)` line is new.)

- [ ] **Step 4: Hook `restockItems`**

The existing private method (originally lines 330-341) becomes:

```java
    private void restockItems(Order order) {
        order.getItems().forEach(orderItem -> {
            ProductVariant variant = productVariantRepository.findById(orderItem.getVariant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm."));
            variant.setStock(variant.getStock() + orderItem.getQuantity());
            try {
                productVariantRepository.save(variant);
                sapoInventorySyncService.pushStock(variant.getId());
            } catch (OptimisticLockingFailureException ex) {
                throw new IllegalStateException("Sản phẩm vừa được cập nhật. Vui lòng thử lại.", ex);
            }
        });
    }
```

(Keep the original exception message text and `OptimisticLockingFailureException` catch from the current file — only the `sapoInventorySyncService.pushStock(...)` line inside the `try` is new.)

- [ ] **Step 5: Write the failing test for `OrderServiceImpl`'s new hooks**

```java
package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SapoInventorySyncService sapoInventorySyncService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void restockItems_SaveSucceeds_PushesStockToSapo() {
        ProductVariant variant = ProductVariant.builder().id(10L).stock(5).build();
        OrderItem orderItem = OrderItem.builder().variant(variant).quantity(3).build();
        Order order = Order.builder().orderNumber("ORD-0001").items(List.of(orderItem)).build();

        when(productVariantRepository.findById(10L)).thenReturn(java.util.Optional.of(variant));

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderService, "restockItems", order);

        verify(sapoInventorySyncService, times(1)).pushStock(10L);
    }

    @Test
    void decreaseStock_ZeroAffected_ThrowsAndDoesNotPushStock() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-001").build();
        com.fashionvista.backend.entity.CartItem cartItem = com.fashionvista.backend.entity.CartItem.builder()
                .variant(variant)
                .quantity(3)
                .build();

        when(productVariantRepository.decreaseStockIfEnough(10L, 3)).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderService, "decreaseStock", cartItem));

        verify(sapoInventorySyncService, never()).pushStock(eq(10L));
    }
}
```

**Note:** if `Order`, `OrderItem`, `CartItem`, or `ProductVariant` do not expose a `@Builder` (verify against each entity's actual annotations — `ProductVariant` is confirmed `@Builder` per Task 1's referenced conventions), construct the test fixtures with their real no-arg constructor + setters instead. Do not change the entities to add `@Builder` as part of this task.

- [ ] **Step 6: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=OrderServiceImplTest`
Expected: FAIL — `sapoInventorySyncService` field/constructor arg not present yet.

- [ ] **Step 7: Apply the Step 1-4 edits to `OrderServiceImpl.java`**, then run the test again.

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=OrderServiceImplTest`
Expected: PASS (2/2)

- [ ] **Step 8: Hook `AdminOrderServiceImpl.addOrderItem`**

Add `import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;` to the import block, and this field after line 67 (the last existing field, `private final SapoOrderSyncService sapoOrderSyncService;`):

```java
    private final SapoInventorySyncService sapoInventorySyncService;
```

Then change the existing block (originally lines 737-745):

```java
        if (finalVariant != null && shouldAffectStock(order)) {
            int affected = productVariantRepository.decreaseStockIfEnough(
                finalVariant.getId(),
                request.getQuantity()
            );
            if (affected == 0) {
                throw new IllegalArgumentException("Sản phẩm không đủ tồn kho.");
            }
            sapoInventorySyncService.pushStock(finalVariant.getId());
        }
```

(Keep the original `shouldAffectStock(order)` guard and exception message from the current file — only the final `sapoInventorySyncService.pushStock(...)` line is new.)

- [ ] **Step 9: Add a test to the existing `AdminOrderServiceImplTest.java`**

Add `@Mock private SapoInventorySyncService sapoInventorySyncService;` alongside the file's existing `@Mock private SapoOrderSyncService sapoOrderSyncService;` field, then add a test following the same structure as this file's existing `updateOrderStatus_TransitionsIntoConfirmed_TriggersSapoOrderPush` test (same order/product/variant fixture setup pattern), asserting `verify(sapoInventorySyncService).pushStock(<variantId>);` after a successful `addOrderItem(...)` call. Use the existing test file's own request/fixture-building helpers for `AddOrderItemRequest` (fields: `productId`, optional `variantId`, `quantity`) rather than duplicating setup logic — read the neighboring passing tests in that file for the exact `addOrderItem` call shape before writing this one, since `AdminOrderServiceImpl` has additional constructor dependencies beyond what's listed here (`orderRepository`, `orderHistoryRepository`, `orderItemRepository`, `refundRepository`, `paymentRepository`, `productRepository`, `productVariantRepository`, `objectMapper`, `userContextService`, `emailService`, `loyaltyService`, `sapoOrderSyncService`, plus the new `sapoInventorySyncService`) that must all be mocked consistently with the rest of the file.

- [ ] **Step 10: Run `AdminOrderServiceImplTest` to verify the new test passes without breaking existing ones**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=AdminOrderServiceImplTest`
Expected: PASS (all tests, including the new one)

- [ ] **Step 11: Hook `AdminReturnServiceImpl.restockReturnItemsIfNeeded`**

Add `import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;` after line 13 (`import com.fashionvista.backend.repository.ProductVariantRepository;`), and this field after line 30 (the last existing field, `private final ProductVariantRepository productVariantRepository;`):

```java
    private final SapoInventorySyncService sapoInventorySyncService;
```

Then change lines 182-183 from:

```java
            variant.setStock(variant.getStock() + delta);
            productVariantRepository.save(variant);
```

to:

```java
            variant.setStock(variant.getStock() + delta);
            productVariantRepository.save(variant);
            sapoInventorySyncService.pushStock(variant.getId());
```

- [ ] **Step 12: Write the failing test for `AdminReturnServiceImpl`**

```java
package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.UpdateReturnStatusRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.ReturnItem;
import com.fashionvista.backend.entity.ReturnRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.repository.ReturnRequestRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminReturnServiceImplTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SapoInventorySyncService sapoInventorySyncService;

    @InjectMocks
    private AdminReturnServiceImpl adminReturnService;

    @Test
    void updateStatus_ApprovedWithRestock_PushesStockToSapoForEachRestockedVariant() {
        ProductVariant variant = ProductVariant.builder().id(20L).stock(10).build();
        OrderItem orderItem = OrderItem.builder().id(1L).variant(variant).build();
        ReturnItem returnItem = ReturnItem.builder()
                .orderItem(orderItem)
                .status(ReturnStatus.APPROVED)
                .quantity(2)
                .acceptedQuantity(2)
                .build();
        Order order = Order.builder().id(100L).paymentMethod(PaymentMethod.COD).items(List.of(orderItem)).build();
        ReturnRequest returnRequest = ReturnRequest.builder()
                .id(1L)
                .order(order)
                .items(List.of(returnItem))
                .build();

        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        when(productVariantRepository.findById(20L)).thenReturn(Optional.of(variant));
        when(returnRequestRepository.save(returnRequest)).thenReturn(returnRequest);

        UpdateReturnStatusRequest request = new UpdateReturnStatusRequest();
        request.setStatus(ReturnStatus.APPROVED);
        request.setRestockItems(true);

        adminReturnService.updateStatus(1L, request);

        verify(sapoInventorySyncService, times(1)).pushStock(eq(20L));
    }
}
```

**Note:** `ReturnItem.getRestockedQuantity()`/`getRestocked()` and `UpdateReturnStatusRequest`'s exact setter names must match the real classes — if `UpdateReturnStatusRequest` uses a different field name than `restockItems` (verify against `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\dto\UpdateReturnStatusRequest.java`), adjust the setter call to match; do not modify the DTO.

- [ ] **Step 13: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=AdminReturnServiceImplTest`
Expected: FAIL — `sapoInventorySyncService` field/constructor arg not present yet.

- [ ] **Step 14: Run test to verify it passes** (after applying Step 11's edits)

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=AdminReturnServiceImplTest`
Expected: PASS

- [ ] **Step 15: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/service/impl/OrderServiceImpl.java src/main/java/com/fashionvista/backend/service/impl/AdminOrderServiceImpl.java src/main/java/com/fashionvista/backend/service/impl/AdminReturnServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/OrderServiceImplTest.java src/test/java/com/fashionvista/backend/service/impl/AdminReturnServiceImplTest.java src/test/java/com/fashionvista/backend/service/impl/AdminOrderServiceImplTest.java
git commit -m "feat(sapo): push stock to Sapo on every order/return stock mutation"
```

---

### Task 7: `InventorySyncHealthCheck`

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\repository\ProductVariantRepository.java` (insert after line 48)
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\InventorySyncHealthCheck.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\synchealth\InventorySyncHealthCheckTest.java`

**Interfaces:**
- Consumes: `ProductVariantRepository.findBySapoVariantIdIsNotNull()` (new), `SapoApiClient.getProductVariants` (Task 5).
- Produces: a `SapoSyncHealthCheck` bean auto-collected by `SyncHealthScheduler` (Task 4) — no changes needed to `SyncHealthScheduler.java` itself.

- [ ] **Step 1: Add the repository method**

Insert after line 48 (end of `ProductVariantRepository.java`, after `sumStockByProductId`):

```java

    List<ProductVariant> findBySapoVariantIdIsNotNull();
```

- [ ] **Step 2: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductVariantsResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySyncHealthCheckTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SapoApiClient sapoApiClient;

    @InjectMocks
    private InventorySyncHealthCheck inventorySyncHealthCheck;

    @Test
    void checkAll_StockMismatch_ReturnsValueMismatchCandidate() {
        Product product = Product.builder().id(1L).sapoProductId("sapo-prod-1").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L).product(product).sku("SKU-001").stock(17).sapoVariantId("sapo-variant-10").build();
        when(productVariantRepository.findBySapoVariantIdIsNotNull()).thenReturn(List.of(variant));

        SapoProductVariantsResponse.Variant remote = new SapoProductVariantsResponse.Variant();
        remote.setId("sapo-variant-10");
        remote.setInventoryQuantity(20);
        SapoProductVariantsResponse response = new SapoProductVariantsResponse();
        response.setVariants(List.of(remote));
        when(sapoApiClient.getProductVariants("sapo-prod-1")).thenReturn(response);

        List<DiscrepancyCandidate> candidates = inventorySyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(10L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.VALUE_MISMATCH);
    }

    @Test
    void checkAll_StockMatches_ReturnsNoCandidates() {
        Product product = Product.builder().id(1L).sapoProductId("sapo-prod-1").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L).product(product).sku("SKU-001").stock(20).sapoVariantId("sapo-variant-10").build();
        when(productVariantRepository.findBySapoVariantIdIsNotNull()).thenReturn(List.of(variant));

        SapoProductVariantsResponse.Variant remote = new SapoProductVariantsResponse.Variant();
        remote.setId("sapo-variant-10");
        remote.setInventoryQuantity(20);
        SapoProductVariantsResponse response = new SapoProductVariantsResponse();
        response.setVariants(List.of(remote));
        when(sapoApiClient.getProductVariants("sapo-prod-1")).thenReturn(response);

        List<DiscrepancyCandidate> candidates = inventorySyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_SapoApiThrows_ReturnsEmptyAndDoesNotThrow() {
        Product product = Product.builder().id(1L).sapoProductId("sapo-prod-1").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L).product(product).sku("SKU-001").stock(17).sapoVariantId("sapo-variant-10").build();
        when(productVariantRepository.findBySapoVariantIdIsNotNull()).thenReturn(List.of(variant));
        when(sapoApiClient.getProductVariants("sapo-prod-1")).thenThrow(new RuntimeException("Sapo down"));

        List<DiscrepancyCandidate> candidates = inventorySyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=InventorySyncHealthCheckTest`
Expected: FAIL — `InventorySyncHealthCheck` class not defined yet.

- [ ] **Step 4: Implement `InventorySyncHealthCheck`**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductVariantsResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventorySyncHealthCheck implements SapoSyncHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(InventorySyncHealthCheck.class);

    private final ProductVariantRepository productVariantRepository;
    private final SapoApiClient sapoApiClient;

    @Override
    public SyncDomain domain() {
        return SyncDomain.INVENTORY;
    }

    @Override
    public List<DiscrepancyCandidate> checkAll() {
        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        List<ProductVariant> variants = productVariantRepository.findBySapoVariantIdIsNotNull();

        Map<String, List<ProductVariant>> variantsByProduct = new HashMap<>();
        for (ProductVariant variant : variants) {
            if (variant.getProduct() == null || variant.getProduct().getSapoProductId() == null) {
                continue;
            }
            variantsByProduct
                    .computeIfAbsent(variant.getProduct().getSapoProductId(), k -> new ArrayList<>())
                    .add(variant);
        }

        for (Map.Entry<String, List<ProductVariant>> entry : variantsByProduct.entrySet()) {
            try {
                SapoProductVariantsResponse response = sapoApiClient.getProductVariants(entry.getKey());
                if (response == null || response.getVariants() == null) {
                    continue;
                }
                Map<String, Integer> remoteStockByVariantId = new HashMap<>();
                for (SapoProductVariantsResponse.Variant remote : response.getVariants()) {
                    remoteStockByVariantId.put(remote.getId(),
                            remote.getInventoryQuantity() != null ? remote.getInventoryQuantity() : 0);
                }

                for (ProductVariant variant : entry.getValue()) {
                    Integer remoteStock = remoteStockByVariantId.get(variant.getSapoVariantId());
                    if (remoteStock == null) {
                        continue;
                    }
                    int localStock = variant.getStock() != null ? variant.getStock() : 0;
                    if (!remoteStock.equals(localStock)) {
                        candidates.add(new DiscrepancyCandidate(
                                variant.getId(),
                                variant.getSku(),
                                DiscrepancyType.VALUE_MISMATCH,
                                "DB stock=" + localStock + ", Sapo stock=" + remoteStock));
                    }
                }
            } catch (RuntimeException ex) {
                log.error("Sapo inventory health check failed for product sapoProductId={}: {}",
                        entry.getKey(), ex.getMessage(), ex);
            }
        }

        return candidates;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=InventorySyncHealthCheckTest`
Expected: PASS (3/3)

- [ ] **Step 6: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/repository/ProductVariantRepository.java src/main/java/com/fashionvista/backend/integration/sapo/synchealth/InventorySyncHealthCheck.java src/test/java/com/fashionvista/backend/integration/sapo/synchealth/InventorySyncHealthCheckTest.java
git commit -m "feat(sapo): add periodic InventorySyncHealthCheck"
```

---

### Task 8: `OrderSyncHealthCheck`

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\repository\OrderRepository.java` (insert after line 51 — do NOT modify `findBySapoSyncStatusAndStatus` at line 51)
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\OrderSyncHealthCheck.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\synchealth\OrderSyncHealthCheckTest.java`

**Interfaces:**
- Consumes: `OrderRepository.findByStatusInAndSapoSyncStatusNot(List<OrderStatus>, SapoSyncStatus)` (new).
- Produces: a `SapoSyncHealthCheck` bean auto-collected by `SyncHealthScheduler` (Task 4) — no changes needed to `SyncHealthScheduler.java` itself. This closes the production gap where Order 8 (`PENDING`→`PROCESSING`) and Order 9 (`FAILED`, moved to `SHIPPING`) fall outside `SapoOrderSyncService.retryFailedSyncs()`'s filter.

- [ ] **Step 1: Add the repository method**

Insert after line 51 (`findBySapoSyncStatusAndStatus`, unchanged) in `OrderRepository.java`:

```java

    List<Order> findByStatusInAndSapoSyncStatusNot(List<OrderStatus> statuses, SapoSyncStatus sapoSyncStatus);
```

- [ ] **Step 2: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSyncHealthCheckTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderSyncHealthCheck orderSyncHealthCheck;

    @Test
    void checkAll_PendingNeverSynced_ReturnsNotSyncedCandidate() {
        Order order = Order.builder().id(8L).orderNumber("ORD-0008")
                .status(OrderStatus.PROCESSING).sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(orderRepository.findByStatusInAndSapoSyncStatusNot(ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SapoSyncStatus.SYNCED)))
                .thenReturn(List.of(order));

        List<DiscrepancyCandidate> candidates = orderSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(8L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.NOT_SYNCED);
    }

    @Test
    void checkAll_FailedAfterLeavingConfirmed_ReturnsSyncFailedCandidate() {
        Order order = Order.builder().id(9L).orderNumber("ORD-0009")
                .status(OrderStatus.SHIPPING).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        when(orderRepository.findByStatusInAndSapoSyncStatusNot(ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SapoSyncStatus.SYNCED)))
                .thenReturn(List.of(order));

        List<DiscrepancyCandidate> candidates = orderSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(9L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.SYNC_FAILED);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=OrderSyncHealthCheckTest`
Expected: FAIL — `OrderSyncHealthCheck` class not defined yet.

- [ ] **Step 4: Implement `OrderSyncHealthCheck`**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderSyncHealthCheck implements SapoSyncHealthCheck {

    private static final List<OrderStatus> TRACKED_STATUSES = List.of(
            OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPING, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    @Override
    public SyncDomain domain() {
        return SyncDomain.ORDER;
    }

    @Override
    public List<DiscrepancyCandidate> checkAll() {
        List<Order> unsynced = orderRepository.findByStatusInAndSapoSyncStatusNot(
                TRACKED_STATUSES, SapoSyncStatus.SYNCED);

        return unsynced.stream()
                .map(order -> new DiscrepancyCandidate(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getSapoSyncStatus() == SapoSyncStatus.FAILED
                                ? DiscrepancyType.SYNC_FAILED
                                : DiscrepancyType.NOT_SYNCED,
                        "Order status=" + order.getStatus() + ", sapoSyncStatus=" + order.getSapoSyncStatus()))
                .toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=OrderSyncHealthCheckTest`
Expected: PASS (2/2)

- [ ] **Step 6: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/repository/OrderRepository.java src/main/java/com/fashionvista/backend/integration/sapo/synchealth/OrderSyncHealthCheck.java src/test/java/com/fashionvista/backend/integration/sapo/synchealth/OrderSyncHealthCheckTest.java
git commit -m "feat(sapo): add periodic OrderSyncHealthCheck closing the retry-gap"
```

---

### Task 9: `SapoOrderSyncService.linkSapoOrder`

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\service\SapoOrderSyncService.java` (insert after line 129, before closing brace at line 130)
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\service\SapoOrderSyncServiceTest.java` (new)

**Interfaces:**
- Consumes: `OrderRepository.findById`, `OrderRepository.findBySapoOrderId` (both existing, confirmed in `OrderRepository.java`).
- Produces: `void linkSapoOrder(Long orderId, String sapoOrderId)` — consumed by Task 10's `AdminSyncHealthController`.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoOrderSyncServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SapoOrderSyncService sapoOrderSyncService;

    @Test
    void linkSapoOrder_NotYetLinkedElsewhere_SetsSyncedFields() {
        Order order = Order.builder().id(9L).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(orderRepository.findBySapoOrderId("sapo-order-9")).thenReturn(Optional.empty());
        when(orderRepository.save(order)).thenReturn(order);

        sapoOrderSyncService.linkSapoOrder(9L, "sapo-order-9");

        assertThat(order.getSapoOrderId()).isEqualTo("sapo-order-9");
        assertThat(order.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoSyncError()).isNull();
    }

    @Test
    void linkSapoOrder_AlreadyLinkedToDifferentOrder_ThrowsIllegalArgumentException() {
        Order order = Order.builder().id(9L).build();
        Order otherOrder = Order.builder().id(2L).build();
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(orderRepository.findBySapoOrderId("sapo-order-2")).thenReturn(Optional.of(otherOrder));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sapoOrderSyncService.linkSapoOrder(9L, "sapo-order-2"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SapoOrderSyncServiceTest`
Expected: FAIL — `linkSapoOrder` method not defined yet.

- [ ] **Step 3: Implement `linkSapoOrder`**

Insert after line 129 (before the class's closing brace at line 130):

```java

    @Transactional
    public void linkSapoOrder(Long orderId, String sapoOrderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        orderRepository.findBySapoOrderId(sapoOrderId).ifPresent(existing -> {
            if (!existing.getId().equals(orderId)) {
                throw new IllegalArgumentException("Sapo order ID này đã được liên kết với đơn hàng khác.");
            }
        });

        order.setSapoOrderId(sapoOrderId);
        order.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        order.setSapoSyncError(null);
        order.setSapoSyncedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
```

This relies on `LocalDateTime`, `Order`, `SapoSyncStatus`, `Transactional`, and `orderRepository` all already being imported/available in this file — confirmed, since the existing `applySuccess` method already calls `LocalDateTime.now()` for the equivalent Product-domain field.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=SapoOrderSyncServiceTest`
Expected: PASS (2/2)

- [ ] **Step 5: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/service/SapoOrderSyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoOrderSyncServiceTest.java
git commit -m "feat(sapo): add linkSapoOrder for manual order-discrepancy remediation"
```

---

### Task 10: `AdminSyncHealthController`

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\dto\SyncDiscrepancyResponse.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\dto\LinkSapoOrderRequest.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\controller\AdminSyncHealthController.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\controller\AdminSyncHealthControllerTest.java`

**Interfaces:**
- Consumes: `SyncDiscrepancyService` (Task 2), `SyncHealthScheduler.runNow()` (Task 4), `SapoInventorySyncService.pushStock`/`pullStock` (Task 5), `SapoOrderSyncService.pushOrder`/`linkSapoOrder` (existing + Task 9).
- Produces: REST endpoints under `/api/admin/sapo/sync-health` — consumed by Task 11's `adminSyncHealthService.ts`.

- [ ] **Step 1: Create `SyncDiscrepancyResponse` DTO**

```java
package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDomain;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SyncDiscrepancyResponse {
    Long id;
    SyncDomain domain;
    Long entityId;
    String entityLabel;
    DiscrepancyType discrepancyType;
    String details;
    LocalDateTime detectedAt;
    LocalDateTime lastSeenAt;
    LocalDateTime resolvedAt;
}
```

- [ ] **Step 2: Create `LinkSapoOrderRequest` DTO**

```java
package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LinkSapoOrderRequest {
    @NotBlank(message = "Sapo Order ID không được để trống")
    private String sapoOrderId;
}
```

- [ ] **Step 3: Write the failing test for `AdminSyncHealthController`**

```java
package com.fashionvista.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.LinkSapoOrderRequest;
import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.integration.sapo.service.SapoOrderSyncService;
import com.fashionvista.backend.integration.sapo.synchealth.SyncDiscrepancyService;
import com.fashionvista.backend.integration.sapo.synchealth.SyncHealthScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class AdminSyncHealthControllerTest {

    private SyncDiscrepancyService syncDiscrepancyService;
    private SyncHealthScheduler syncHealthScheduler;
    private SapoInventorySyncService sapoInventorySyncService;
    private SapoOrderSyncService sapoOrderSyncService;
    private AdminSyncHealthController controller;

    @BeforeEach
    void setUp() {
        syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        syncHealthScheduler = mock(SyncHealthScheduler.class);
        sapoInventorySyncService = mock(SapoInventorySyncService.class);
        sapoOrderSyncService = mock(SapoOrderSyncService.class);
        controller = new AdminSyncHealthController(
                syncDiscrepancyService, syncHealthScheduler, sapoInventorySyncService, sapoOrderSyncService);
    }

    @Test
    void pushToSapo_InventoryDomainSuccess_ResolvesDiscrepancy() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(1L).domain(SyncDomain.INVENTORY).entityId(10L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(1L)).thenReturn(discrepancy);
        when(sapoInventorySyncService.pushStock(10L)).thenReturn(true);

        ResponseEntity<Void> response = controller.pushToSapo(1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void pushToSapo_InventoryDomainFailure_DoesNotResolveDiscrepancy() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(1L).domain(SyncDomain.INVENTORY).entityId(10L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(1L)).thenReturn(discrepancy);
        when(sapoInventorySyncService.pushStock(10L)).thenReturn(false);

        controller.pushToSapo(1L);

        verify(syncDiscrepancyService, never()).resolve(any(SyncDiscrepancy.class));
    }

    @Test
    void pushToSapo_OrderDomain_AlwaysResolvesAfterFireAndForgetPush() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(2L).domain(SyncDomain.ORDER).entityId(9L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(2L)).thenReturn(discrepancy);

        controller.pushToSapo(2L);

        verify(sapoOrderSyncService).pushOrder(9L);
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void pullFromSapo_OrderDomain_ThrowsIllegalArgumentException() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(2L).domain(SyncDomain.ORDER).entityId(9L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(2L)).thenReturn(discrepancy);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.pullFromSapo(2L));
    }

    @Test
    void linkSapoOrder_InventoryDomain_ThrowsIllegalArgumentException() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(1L).domain(SyncDomain.INVENTORY).entityId(10L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(1L)).thenReturn(discrepancy);
        LinkSapoOrderRequest request = new LinkSapoOrderRequest();
        request.setSapoOrderId("sapo-order-9");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.linkSapoOrder(1L, request));
    }

    @Test
    void linkSapoOrder_OrderDomain_LinksAndResolves() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(2L).domain(SyncDomain.ORDER).entityId(9L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(2L)).thenReturn(discrepancy);
        LinkSapoOrderRequest request = new LinkSapoOrderRequest();
        request.setSapoOrderId("sapo-order-9");

        controller.linkSapoOrder(2L, request);

        verify(sapoOrderSyncService).linkSapoOrder(9L, "sapo-order-9");
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void runNow_DelegatesToScheduler() {
        controller.runNow();

        verify(syncHealthScheduler).runNow();
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=AdminSyncHealthControllerTest`
Expected: FAIL — `AdminSyncHealthController` class not defined yet.

- [ ] **Step 5: Implement `AdminSyncHealthController`**

Follows the exact template of the existing `AdminSapoSyncController.java` (`@RestController @RequestMapping @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")`):

```java
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
```

`findByIdOrThrow` already throws `IllegalArgumentException` (Task 2), satisfying the 400-response Global Constraint for not-found discrepancies; the explicit `throw new IllegalArgumentException(...)` calls above cover the domain/action-mismatch 400 cases.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd D:\FashionVista\FashionVista_Backend && ./mvnw test -Dtest=AdminSyncHealthControllerTest`
Expected: PASS (7/7)

- [ ] **Step 7: Commit**

```bash
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/dto/SyncDiscrepancyResponse.java src/main/java/com/fashionvista/backend/dto/LinkSapoOrderRequest.java src/main/java/com/fashionvista/backend/controller/AdminSyncHealthController.java src/test/java/com/fashionvista/backend/controller/AdminSyncHealthControllerTest.java
git commit -m "feat(sapo): add AdminSyncHealthController for manual remediation"
```

---

### Task 11: Admin frontend — types, service, page

**Files:**
- Create: `D:\FashionVista\FashionVista_Admin\src\types\syncHealth.ts`
- Create: `D:\FashionVista\FashionVista_Admin\src\services\adminSyncHealthService.ts`
- Create: `D:\FashionVista\FashionVista_Admin\src\pages\admin\AdminSyncHealth.tsx`

**Interfaces:**
- Consumes: `PageResponse<T>` from `../types/return.ts` (existing); Backend endpoints from Task 10 (`/api/admin/sapo/sync-health/*`).
- Produces: `adminSyncHealthService` object and `AdminSyncHealth` default-exported component — consumed by Task 12's route wiring.

- [ ] **Step 1: Create `src/types/syncHealth.ts`**

```typescript
export type SyncDomain = 'INVENTORY' | 'ORDER';
export type DiscrepancyType = 'NOT_SYNCED' | 'VALUE_MISMATCH' | 'SYNC_FAILED';

export interface SyncDiscrepancy {
  id: number;
  domain: SyncDomain;
  entityId: number;
  entityLabel: string;
  discrepancyType: DiscrepancyType;
  details: string;
  detectedAt: string;
  lastSeenAt: string;
  resolvedAt: string | null;
}
```

- [ ] **Step 2: Create `src/services/adminSyncHealthService.ts`**

Follows the exact template of the existing `adminReturnService.ts` (`axiosClient`, `cachedRequest`/`buildRequestCacheKey`/`setCachedRequestValue`/`clearCachedRequestsByPrefix` from `./requestCache`, `ADMIN_CACHE_TTL_MS = 500`):

```typescript
import { axiosClient } from './axiosClient';
import type { PageResponse } from '../types/return';
import type { SyncDiscrepancy, SyncDomain } from '../types/syncHealth';
import { buildRequestCacheKey, cachedRequest, clearCachedRequestsByPrefix } from './requestCache';

const ADMIN_CACHE_TTL_MS = 500;

export const adminSyncHealthService = {
  async list(params?: { domain?: SyncDomain; status?: 'OPEN' | 'RESOLVED'; page?: number; size?: number }): Promise<PageResponse<SyncDiscrepancy>> {
    return cachedRequest(buildRequestCacheKey('admin:sync-health:discrepancies', params), async () => {
      const response = await axiosClient.get<PageResponse<SyncDiscrepancy>>('/admin/sapo/sync-health/discrepancies', {
        params,
      });
      return response.data;
    }, ADMIN_CACHE_TTL_MS);
  },

  async pushToSapo(id: number): Promise<void> {
    await axiosClient.post(`/admin/sapo/sync-health/discrepancies/${id}/push-to-sapo`);
    clearCachedRequestsByPrefix('admin:sync-health');
  },

  async pullFromSapo(id: number): Promise<void> {
    await axiosClient.post(`/admin/sapo/sync-health/discrepancies/${id}/pull-from-sapo`);
    clearCachedRequestsByPrefix('admin:sync-health');
  },

  async linkSapoOrder(id: number, sapoOrderId: string): Promise<void> {
    await axiosClient.post(`/admin/sapo/sync-health/discrepancies/${id}/link-sapo-order`, { sapoOrderId });
    clearCachedRequestsByPrefix('admin:sync-health');
  },

  async resolve(id: number): Promise<void> {
    await axiosClient.post(`/admin/sapo/sync-health/discrepancies/${id}/resolve`);
    clearCachedRequestsByPrefix('admin:sync-health');
  },

  async runNow(): Promise<void> {
    await axiosClient.post('/admin/sapo/sync-health/run-now');
    clearCachedRequestsByPrefix('admin:sync-health');
  },
};
```

- [ ] **Step 3: Create `src/pages/admin/AdminSyncHealth.tsx`**

Follows the structural precedent of the existing `AdminReturns.tsx` page (plain `useState`/`useEffect` data fetching, `useToast` for feedback, Tailwind classes with the codebase's CSS-variable palette):

```tsx
import { useEffect, useMemo, useState } from 'react';
import { adminSyncHealthService } from '../../services/adminSyncHealthService';
import type { SyncDiscrepancy, SyncDomain } from '../../types/syncHealth';
import { useToast } from '../../hooks/useToast';
import { RefreshCw } from 'lucide-react';

const DOMAIN_OPTIONS: { label: string; value: SyncDomain | '' }[] = [
  { label: 'Tất cả', value: '' },
  { label: 'Kho (Inventory)', value: 'INVENTORY' },
  { label: 'Đơn hàng (Order)', value: 'ORDER' },
];

const STATUS_OPTIONS: { label: string; value: 'OPEN' | 'RESOLVED' | '' }[] = [
  { label: 'Đang mở', value: 'OPEN' },
  { label: 'Đã xử lý', value: 'RESOLVED' },
  { label: 'Tất cả', value: '' },
];

const AdminSyncHealth = () => {
  const [data, setData] = useState<{ content: SyncDiscrepancy[]; totalPages: number; number: number }>({
    content: [],
    totalPages: 0,
    number: 0,
  });
  const [domainFilter, setDomainFilter] = useState<SyncDomain | ''>('');
  const [statusFilter, setStatusFilter] = useState<'OPEN' | 'RESOLVED' | ''>('OPEN');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [runningNow, setRunningNow] = useState(false);
  const [linkingId, setLinkingId] = useState<number | null>(null);
  const [sapoOrderIdInput, setSapoOrderIdInput] = useState('');
  const { showToast } = useToast();

  const filters = useMemo(
    () => ({ domain: domainFilter || undefined, status: statusFilter || undefined, page, size: 20 }),
    [domainFilter, statusFilter, page],
  );

  const fetchList = async () => {
    try {
      setLoading(true);
      const res = await adminSyncHealthService.list(filters);
      setData({ content: res.content, totalPages: res.totalPages, number: res.number });
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Không thể tải danh sách lệch đồng bộ.';
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, [filters]);

  const handleRunNow = async () => {
    try {
      setRunningNow(true);
      await adminSyncHealthService.runNow();
      showToast('Đã chạy kiểm tra đồng bộ.', 'success');
      fetchList();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.message || 'Lỗi khi chạy kiểm tra.', 'error');
    } finally {
      setRunningNow(false);
    }
  };

  const handlePush = async (id: number) => {
    try {
      await adminSyncHealthService.pushToSapo(id);
      showToast('Đã đẩy dữ liệu lên Sapo.', 'success');
      fetchList();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.message || 'Lỗi khi đẩy dữ liệu.', 'error');
    }
  };

  const handlePull = async (id: number) => {
    try {
      await adminSyncHealthService.pullFromSapo(id);
      showToast('Đã lấy dữ liệu từ Sapo.', 'success');
      fetchList();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.message || 'Lỗi khi lấy dữ liệu.', 'error');
    }
  };

  const handleResolve = async (id: number) => {
    try {
      await adminSyncHealthService.resolve(id);
      showToast('Đã đánh dấu xử lý xong.', 'success');
      fetchList();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.message || 'Lỗi khi đánh dấu xử lý.', 'error');
    }
  };

  const handleLinkSubmit = async (id: number) => {
    if (!sapoOrderIdInput.trim()) {
      showToast('Vui lòng nhập Sapo Order ID.', 'error');
      return;
    }
    try {
      await adminSyncHealthService.linkSapoOrder(id, sapoOrderIdInput.trim());
      showToast('Đã liên kết đơn hàng với Sapo.', 'success');
      setLinkingId(null);
      setSapoOrderIdInput('');
      fetchList();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.message || 'Lỗi khi liên kết đơn hàng.', 'error');
    }
  };

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Đồng bộ Sapo</h1>
          <p className="text-sm text-[var(--muted-foreground)]">Giám sát và xử lý lệch đồng bộ giữa FashionVista và Sapo</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={domainFilter}
            onChange={(e) => { setDomainFilter(e.target.value as SyncDomain | ''); setPage(0); }}
            className="rounded-lg border border-[var(--border)] bg-[var(--input-background)] px-3 py-2 text-sm"
          >
            {DOMAIN_OPTIONS.map((opt) => (
              <option key={opt.label} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value as 'OPEN' | 'RESOLVED' | ''); setPage(0); }}
            className="rounded-lg border border-[var(--border)] bg-[var(--input-background)] px-3 py-2 text-sm"
          >
            {STATUS_OPTIONS.map((opt) => (
              <option key={opt.label} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <button
            type="button"
            onClick={handleRunNow}
            disabled={runningNow}
            className="flex items-center gap-2 rounded-lg bg-[var(--primary)] text-white px-3 py-2 text-sm font-medium hover:opacity-90 disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${runningNow ? 'animate-spin' : ''}`} />
            Chạy kiểm tra ngay
          </button>
        </div>
      </div>

      <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-[var(--muted)]/40 text-[var(--muted-foreground)]">
              <tr>
                <th className="px-4 py-3 text-left">Domain</th>
                <th className="px-4 py-3 text-left">Đối tượng</th>
                <th className="px-4 py-3 text-left">Loại lệch</th>
                <th className="px-4 py-3 text-left">Chi tiết</th>
                <th className="px-4 py-3 text-left">Phát hiện lúc</th>
                <th className="px-4 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {!loading && data.content.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-[var(--muted-foreground)]">Không có lệch đồng bộ.</td></tr>
              )}
              {data.content.map((item) => (
                <tr key={item.id} className="border-t border-[var(--border)] hover:bg-[var(--muted)]/30 transition-colors align-top">
                  <td className="px-4 py-3 font-medium">{item.domain}</td>
                  <td className="px-4 py-3">{item.entityLabel}</td>
                  <td className="px-4 py-3">{item.discrepancyType}</td>
                  <td className="px-4 py-3 text-[var(--muted-foreground)] max-w-xs truncate" title={item.details}>{item.details}</td>
                  <td className="px-4 py-3">{new Date(item.detectedAt).toLocaleString('vi-VN')}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex flex-col items-end gap-1">
                      {!item.resolvedAt && (
                        <div className="flex flex-wrap justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => handlePush(item.id)}
                            className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)]"
                          >
                            Đẩy lên Sapo
                          </button>
                          {item.domain === 'INVENTORY' && (
                            <button
                              type="button"
                              onClick={() => handlePull(item.id)}
                              className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)]"
                            >
                              Lấy từ Sapo
                            </button>
                          )}
                          {item.domain === 'ORDER' && (
                            <button
                              type="button"
                              onClick={() => { setLinkingId(item.id); setSapoOrderIdInput(''); }}
                              className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)]"
                            >
                              Liên kết Sapo
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => handleResolve(item.id)}
                            className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)]"
                          >
                            Đánh dấu xong
                          </button>
                        </div>
                      )}
                      {item.resolvedAt && (
                        <span className="text-xs text-[var(--muted-foreground)]">Đã xử lý</span>
                      )}
                      {linkingId === item.id && (
                        <div className="flex items-center gap-1 mt-1">
                          <input
                            type="text"
                            placeholder="Sapo Order ID"
                            value={sapoOrderIdInput}
                            onChange={(e) => setSapoOrderIdInput(e.target.value)}
                            className="rounded border border-[var(--border)] px-2 py-1 text-xs w-32"
                          />
                          <button
                            type="button"
                            onClick={() => handleLinkSubmit(item.id)}
                            className="rounded bg-[var(--primary)] text-white px-2 py-1 text-xs hover:opacity-90"
                          >
                            Lưu
                          </button>
                        </div>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-lg border border-[var(--border)] px-3 py-1 text-sm disabled:opacity-50"
          >
            Trước
          </button>
          <span className="text-sm text-[var(--muted-foreground)]">Trang {page + 1} / {data.totalPages}</span>
          <button
            type="button"
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg border border-[var(--border)] px-3 py-1 text-sm disabled:opacity-50"
          >
            Sau
          </button>
        </div>
      )}
    </div>
  );
};

export default AdminSyncHealth;
```

- [ ] **Step 4: Verify via TypeScript compile and lint**

Run: `cd D:\FashionVista\FashionVista_Admin && npx tsc --noEmit`
Expected: no errors related to the three new files.

Run: `cd D:\FashionVista\FashionVista_Admin && npm run lint`
Expected: no errors related to the three new files.

- [ ] **Step 5: Commit**

```bash
cd D:\FashionVista\FashionVista_Admin
git add src/types/syncHealth.ts src/services/adminSyncHealthService.ts src/pages/admin/AdminSyncHealth.tsx
git commit -m "feat(sapo): add admin sync health page, service, and types"
```

---

### Task 12: Route and navigation wiring

**Files:**
- Modify: `D:\FashionVista\FashionVista_Admin\src\routes\AppRoutes.tsx` (lazy import after line 34; route after line 73)
- Modify: `D:\FashionVista\FashionVista_Admin\src\components\layout\AdminLayout.tsx` (icon import; nav entry in 'Hệ thống' group; `PAGE_TITLES` entry)

**Interfaces:**
- Consumes: `AdminSyncHealth` default export (Task 11).

- [ ] **Step 1: Wire the route in `AppRoutes.tsx`**

Insert after line 34 (the last lazy import, `AdminBulkImageUpload`):

```typescript
const AdminSyncHealth = React.lazy(() => import('../pages/admin/AdminSyncHealth'));
```

Insert after line 73 (the last route, `shipping-fee-configs`):

```tsx
<Route path="sync-health" element={<AdminSyncHealth />} />
```

- [ ] **Step 2: Add the nav entry and page title in `AdminLayout.tsx`**

Add `RefreshCw` to the lucide-react icon imports (alphabetically between `Package` and `RotateCcw`, in the existing import block at lines 3-22).

In the `NAV_GROUPS` array, change the `'Hệ thống'` group (currently only `{ label: 'Phí vận chuyển', path: '/shipping-fee-configs', icon: Truck }`) to:

```typescript
    items: [
      { label: 'Phí vận chuyển', path: '/shipping-fee-configs', icon: Truck },
      { label: 'Đồng bộ Sapo', path: '/sync-health', icon: RefreshCw },
    ],
```

In the `PAGE_TITLES` record, add after the `'/shipping-fee-configs': 'Phí vận chuyển',` line:

```typescript
  '/sync-health': 'Đồng bộ Sapo',
```

- [ ] **Step 3: Verify via TypeScript compile, lint, and manual check**

Run: `cd D:\FashionVista\FashionVista_Admin && npx tsc --noEmit`
Expected: no errors.

Run: `cd D:\FashionVista\FashionVista_Admin && npm run lint`
Expected: no errors.

Then start the dev server (`npm run dev`) and manually confirm: the "Đồng bộ Sapo" nav item appears under "Hệ thống", clicking it navigates to `/sync-health`, the page title shows "Đồng bộ Sapo", and the table/filter/run-now controls render without console errors.

- [ ] **Step 4: Commit**

```bash
cd D:\FashionVista\FashionVista_Admin
git add src/routes/AppRoutes.tsx src/components/layout/AdminLayout.tsx
git commit -m "feat(sapo): wire sync health page into admin routes and navigation"
```

---

## Self-Review

**1. Spec coverage** — checked against `docs/superpowers/specs/2026-08-06-sapo-sync-health-design.md`:
- Generic pluggable framework (`SapoSyncHealthCheck` + `SyncHealthScheduler` auto-collecting beans) → Tasks 2, 4. ✅
- `sync_discrepancy` table with dedup key `(domain, entity_id, discrepancy_type)` where `resolved_at IS NULL` → Task 1 + Task 2's `reconcile`. ✅
- 30-minute check interval → Task 4 (`fixedDelay = 1800000`). ✅
- Inventory real-time push-on-event + periodic safety net → Task 5 (service) + Task 6 (hooks) + Task 7 (periodic check). ✅
- Order retry-gap fix (Order 8/9 scenario) → Task 8 (`OrderSyncHealthCheck`, additive, doesn't touch `retryFailedSyncs()`). ✅
- One deduplicated admin alert email per newly-detected discrepancy → Task 3 (email) + Task 4 (`markAlertSent` after send, only for `allNewlyDetected`). ✅
- Admin page: manual push, pull, special-case remediation → Task 10 (controller: push/pull/link/resolve) + Task 11 (page UI exposing all four actions per-domain). ✅
- Admin API base `/api/admin/sapo/sync-health`, `@PreAuthorize("hasRole('ADMIN')")`, all 6 endpoints → Task 10. ✅
- Domain/action mismatch → 400 via `IllegalArgumentException` → Task 10 (explicit throws) + Task 2 (`findByIdOrThrow`). ✅
- Order push resolves immediately (fire-and-forget `@Async`); Inventory push only resolves on `true` → Task 10's `pushToSapo` branches. ✅
- Admin UI table columns (Domain, Entity, Discrepancy type, Details, Detected at, Actions) → Task 11. ✅
- Nav entry + route → Task 12. ✅
- Error isolation (one domain's failure doesn't block others) → Task 4's try/catch per check. ✅
- Real-time push never blocks customer flow (never throws) → Task 5's `pushStock` catches `RuntimeException` and returns `false`. ✅
- Unit tests per health check, dedup service, scheduler (survives one throwing), controller per-endpoint incl. 400 case, inventory push success/failure → Tasks 2, 4, 5, 7, 8, 9, 10. ✅
- Out-of-scope items (Product admin UI, unified dashboard, Customer/Voucher/Shipping/Ledger, auto-remediation, rate-limit batching) are correctly excluded from every task above. ✅

**2. Placeholder scan** — no "TBD"/"TODO"/"implement later" found. Two spots carry explicit verification notes rather than placeholders: Task 3 Step 4 (constructor argument order for `EmailServiceImpl`'s test) and Task 6 Step 9 (exact `AddOrderItemRequest` usage pattern) — both instruct verifying against the real file before writing, not "fill in later"; the actual code to adapt is fully given in both cases.

**3. Type/signature consistency** — verified across tasks:
- `DiscrepancyCandidate(Long entityId, String entityLabel, DiscrepancyType discrepancyType, String details)` used identically in Tasks 2, 4, 7, 8.
- `SapoSyncHealthCheck.domain(): SyncDomain` / `checkAll(): List<DiscrepancyCandidate>` implemented identically by `InventorySyncHealthCheck` (Task 7) and `OrderSyncHealthCheck` (Task 8).
- `SyncDiscrepancyService.reconcile(SyncDomain, List<DiscrepancyCandidate>): List<SyncDiscrepancy>`, `markAlertSent(List<SyncDiscrepancy>): void`, `find(SyncDomain, Boolean, Pageable): Page<SyncDiscrepancy>`, `findByIdOrThrow(Long): SyncDiscrepancy`, `resolve(SyncDiscrepancy): void` — all called with matching signatures in Task 4 (scheduler) and Task 10 (controller).
- `SapoInventorySyncService.pushStock(Long): boolean` / `pullStock(Long): boolean` (Task 5) called identically in Task 6 (hooks, `pushStock` only), Task 10 (controller, both).
- `SapoOrderSyncService.linkSapoOrder(Long, String): void` (Task 9) called identically in Task 10.
- `EmailService.sendSyncDiscrepancyAlert(List<SyncDiscrepancy>): void` (Task 3) called identically in Task 4.
- Frontend `SyncDiscrepancy` TS interface (Task 11) field names (`id, domain, entityId, entityLabel, discrepancyType, details, detectedAt, lastSeenAt, resolvedAt`) match `SyncDiscrepancyResponse`'s JSON serialization (Task 10) field-for-field (Jackson serializes `@Value` getters as camelCase JSON keys matching these names).
- `adminSyncHealthService` method names (`list, pushToSapo, pullFromSapo, linkSapoOrder, resolve, runNow`) match the endpoints and semantics defined in Task 10, and are called with matching names in `AdminSyncHealth.tsx` (Task 11).

No gaps found. Plan is complete and internally consistent.
