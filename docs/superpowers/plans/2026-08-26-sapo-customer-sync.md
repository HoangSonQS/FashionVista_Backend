# Sapo Customer Outbound Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push FashionVista `User` (role `CUSTOMER`) records to Sapo as `Customer` resources on registration and on profile update, reusing the existing outbound-sync infrastructure (`SapoApiClient`, `SapoSyncStatus`, per-domain `@Async` executor, after-commit scheduling pattern) established by the Order/Product/Inventory/Voucher integrations.

**Architecture:** A new `SapoCustomerSyncService` mirrors `SapoVoucherSyncService`'s `@Async` fire-and-forget push pattern: one `pushCustomer(Long userId)` method that creates or updates the Sapo `Customer` depending on whether `User.sapoCustomerId` is already set. A new `SapoNameSplitter` util converts FashionVista's single `fullName` field into Sapo's `first_name`/`last_name` pair. `AuthServiceImpl.register()` schedules the push via `TransactionSynchronizationManager.afterCommit()` (identical helper-method shape to `AdminVoucherServiceImpl.schedulePushVoucherAfterCommit`), since `register()` runs inside an open `@Transactional` boundary. `UserController.updateProfile()` calls `pushCustomer()` directly with no defer wrapper, since Spring Data's per-call transaction has already committed by the time the controller method resumes control. Two new `SapoApiClient` methods (`createCustomer`, `updateCustomer`) follow the client's existing REST method pattern exactly. No new `SyncDomain` value, no sync-health check, no periodic reconciliation — this is push-only, matching the spec's Non-goals.

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring Data JPA, Spring `@Async` + `TransactionSynchronizationManager`, Maven, JUnit 5 + Mockito + AssertJ.

**Spec:** `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\docs\superpowers\specs\2026-08-26-sapo-customer-sync-design.md`

## Global Constraints

- Zero new environment variables, config keys, or Maven dependencies — reuses the existing `SapoOutboundProperties` (`apiKey`/`apiSecret`/`storeDomain`) and `SapoApiClient`'s `RestClient` that Product/Order/Voucher pushes already use.
- Sapo Customer Admin API paths, verified against `support.sapo.vn` during spec design (per Backend CLAUDE.md Sapo Integration Rule): `POST /admin/customers.json` (create), `PUT /admin/customers/{id}.json` (update). Do not deviate from these paths without re-verifying against `support.sapo.vn`.
- Field mapping (verbatim from spec): `fullName` → `first_name`/`last_name` via `SapoNameSplitter.splitLastName(fullName)` (split on the last space; the trailing token becomes `last_name`, everything before it becomes `first_name`; no space in the name → `first_name` = whole string, `last_name` = empty string). `email` → `email` direct. `phoneNumber` → `phone` direct. `gender` (`Gender.MALE/FEMALE/OTHER`) → `"Male"/"Female"/"Other"` string literals (NOT `.name()`). `dateOfBirth` (`LocalDate`) → `dob` via `.toString()` (already `yyyy-MM-dd`). `tier` (`CustomerTier.BRONZE/SILVER/GOLD/PLATINUM`) → `tags` as a single fixed string `fashionvista_tier_<lowercase-tier>` (e.g. `fashionvista_tier_gold`), fully replacing whatever tags exist on the Sapo side. `role` is a filter only (only `UserRole.CUSTOMER` is pushed) — never mapped to a Sapo field.
- Role filter is enforced inside `SapoCustomerSyncService.pushCustomer()` only — callers (`AuthServiceImpl`, `UserController`) always call it unconditionally; the service itself decides to skip non-`CUSTOMER` users.
- No `SyncDomain.CUSTOMER` value is added, no sync-health/reconciliation check, no address sync, no admin ban/status-change integration, no periodic pull — all explicit Non-goals in the spec.
- `SapoApiClient`'s two new methods (`createCustomer`, `updateCustomer`) get **no dedicated test** in `SapoApiClientTest.java` — per spec, they are exercised indirectly through `SapoCustomerSyncServiceTest`'s mocked-`SapoApiClient` tests. This is a deliberate departure from Voucher's Task 4, which did add dedicated `MockRestServiceServer` tests.
- No repository-level tests exist anywhere in this codebase (established convention) — the `User` entity field addition in Task 1 gets a compile check only, no dedicated test file.
- One commit per task. Never modify `.env` files. Never push without explicit confirmation.

---

### Task 1: User entity Sapo sync fields

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\entity\User.java`

**Interfaces:**
- Produces: `User.sapoCustomerId` (`Long`, nullable), `User.sapoSyncStatus` (`SapoSyncStatus`, defaults to `PENDING`) — consumed by every later task.

- [ ] **Step 1: Add the two new columns to `User.java`**

`SapoSyncStatus` lives in the same `com.fashionvista.backend.entity` package as `User`, so no new import is needed. Insert the two fields right after `lastLoginAt` (after line 98), before the `// Relationships` comment:

```java
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "sapo_customer_id")
    private Long sapoCustomerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_sync_status", nullable = false, length = 32)
    @Builder.Default
    private SapoSyncStatus sapoSyncStatus = SapoSyncStatus.PENDING;

    // Relationships
```

- [ ] **Step 2: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw compile -q
```
Expected: `BUILD SUCCESS`. (`spring.jpa.hibernate.ddl-auto=update` creates the two new `users` columns automatically on next app start — no migration script needed.)

- [ ] **Step 3: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/entity/User.java
git commit -m "feat(customer): add Sapo sync fields to User entity"
```

---

### Task 2: SapoNameSplitter util

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\integration\sapo\util\SapoNameSplitter.java`
- Test: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\test\java\com\fashionvista\backend\integration\sapo\util\SapoNameSplitterTest.java`

**Interfaces:**
- Produces: `SapoNameSplitter.splitLastName(String fullName): SapoNameSplitter.Split` where `Split` exposes `getFirstName(): String` and `getLastName(): String` — consumed by Task 6 (`SapoCustomerSyncService`).

- [ ] **Step 1: Write the failing test file**

```java
package com.fashionvista.backend.integration.sapo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SapoNameSplitterTest {

    @Test
    void splitLastName_TwoWordName_SplitsFirstAndLast() {
        SapoNameSplitter.Split split = SapoNameSplitter.splitLastName("Nguyen Anh");

        assertThat(split.getFirstName()).isEqualTo("Nguyen");
        assertThat(split.getLastName()).isEqualTo("Anh");
    }

    @Test
    void splitLastName_MultiWordName_SplitsOnLastSpaceOnly() {
        SapoNameSplitter.Split split = SapoNameSplitter.splitLastName("Nguyen Van Anh");

        assertThat(split.getFirstName()).isEqualTo("Nguyen Van");
        assertThat(split.getLastName()).isEqualTo("Anh");
    }

    @Test
    void splitLastName_SingleWordName_LastNameIsEmpty() {
        SapoNameSplitter.Split split = SapoNameSplitter.splitLastName("Madonna");

        assertThat(split.getFirstName()).isEqualTo("Madonna");
        assertThat(split.getLastName()).isEqualTo("");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=SapoNameSplitterTest" -q
```
Expected: FAIL — compile error, `SapoNameSplitter` class does not exist.

- [ ] **Step 3: Create `SapoNameSplitter.java`**

```java
package com.fashionvista.backend.integration.sapo.util;

import lombok.Value;

public final class SapoNameSplitter {

    private SapoNameSplitter() {
    }

    public static Split splitLastName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new Split(trimmed, "");
        }
        String firstName = trimmed.substring(0, lastSpace);
        String lastName = trimmed.substring(lastSpace + 1);
        return new Split(firstName, lastName);
    }

    @Value
    public static class Split {
        String firstName;
        String lastName;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=SapoNameSplitterTest" -q
```
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/integration/sapo/util/SapoNameSplitter.java src/test/java/com/fashionvista/backend/integration/sapo/util/SapoNameSplitterTest.java
git commit -m "feat(customer): add SapoNameSplitter util"
```

---

### Task 3: SapoCustomerRequest / SapoCustomerResponse DTOs

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoCustomerRequest.java`
- Create: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoCustomerResponse.java`

**Interfaces:**
- Produces: `SapoCustomerRequest` (builder: `SapoCustomerRequest.builder().customer(SapoCustomerRequest.Customer.builder()....build()).build()`, nested `Customer` fields `firstName/lastName/email/phone/gender/dob/tags`), `SapoCustomerResponse` (`getCustomer(): SapoCustomerResponse.Customer` exposing `getId(): String`) — consumed by Task 4 (`SapoApiClient`) and Task 6 (`SapoCustomerSyncService`).

- [ ] **Step 1: Create `SapoCustomerRequest.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoCustomerRequest {

    Customer customer;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customer {
        @JsonProperty("first_name")
        String firstName;

        @JsonProperty("last_name")
        String lastName;

        String email;
        String phone;
        String gender;
        String dob;
        String tags;
    }
}
```

- [ ] **Step 2: Create `SapoCustomerResponse.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoCustomerResponse {

    private Customer customer;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Customer {
        private String id;
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoCustomerRequest.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoCustomerResponse.java
git commit -m "feat(customer): add SapoCustomerRequest/SapoCustomerResponse DTOs"
```

---

### Task 4: SapoApiClient.createCustomer / updateCustomer

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\integration\sapo\client\SapoApiClient.java`

**Interfaces:**
- Consumes: `SapoCustomerRequest`, `SapoCustomerResponse` (Task 3).
- Produces: `SapoApiClient.createCustomer(SapoCustomerRequest): SapoCustomerResponse`, `SapoApiClient.updateCustomer(Long sapoCustomerId, SapoCustomerRequest): SapoCustomerResponse` — consumed by Task 6 (`SapoCustomerSyncService`). No dedicated test per Global Constraints — exercised indirectly through `SapoCustomerSyncServiceTest`.

- [ ] **Step 1: Add imports**

Add after the existing `import com.fashionvista.backend.integration.sapo.dto.SapoOrderPushResponse;` line (line 5):

```java
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerResponse;
```

- [ ] **Step 2: Add the two new methods**

Add these methods right after `getProduct` (after line 78, before the final closing `}`):

```java

    public SapoCustomerResponse createCustomer(SapoCustomerRequest request) {
        return restClient.post()
                .uri("/admin/customers.json")
                .body(request)
                .retrieve()
                .body(SapoCustomerResponse.class);
    }

    public SapoCustomerResponse updateCustomer(Long sapoCustomerId, SapoCustomerRequest request) {
        return restClient.put()
                .uri("/admin/customers/{id}.json", sapoCustomerId)
                .body(request)
                .retrieve()
                .body(SapoCustomerResponse.class);
    }
```

- [ ] **Step 3: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java
git commit -m "feat(customer): add createCustomer/updateCustomer to SapoApiClient"
```

---

### Task 5: sapoCustomerTaskExecutor async bean

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\config\AsyncConfig.java`

**Interfaces:**
- Produces: Spring bean `sapoCustomerTaskExecutor` (name string used by `@Async("sapoCustomerTaskExecutor")`) — consumed by Task 6 (`SapoCustomerSyncService`).

- [ ] **Step 1: Add the new bean**

Add this method right after the existing `sapoOrderTaskExecutor` bean (after line 33, before the final closing `}`):

```java

    @Bean(name = "sapoCustomerTaskExecutor")
    public Executor sapoCustomerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sapo-customer-");
        executor.initialize();
        return executor;
    }
```

- [ ] **Step 2: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/config/AsyncConfig.java
git commit -m "feat(customer): add sapoCustomerTaskExecutor async bean"
```

---

### Task 6: SapoCustomerSyncService

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\integration\sapo\service\SapoCustomerSyncService.java`
- Test: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\test\java\com\fashionvista\backend\integration\sapo\service\SapoCustomerSyncServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository` (existing), `SapoApiClient.createCustomer/updateCustomer` (Task 4), `SapoCustomerRequest`/`SapoCustomerResponse` (Task 3), `SapoNameSplitter.splitLastName` (Task 2), `sapoCustomerTaskExecutor` bean (Task 5), `User.sapoCustomerId/sapoSyncStatus` (Task 1).
- Produces: `SapoCustomerSyncService.pushCustomer(Long userId): void` (`@Async`) — consumed by Task 7 (`AuthServiceImpl`) and Task 8 (`UserController`).

- [ ] **Step 1: Write the failing test file**

```java
package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.CustomerTier;
import com.fashionvista.backend.entity.Gender;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerResponse;
import com.fashionvista.backend.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoCustomerSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SapoCustomerSyncService sapoCustomerSyncService;

    private static SapoCustomerResponse customerResponse(String id) {
        SapoCustomerResponse.Customer customer = new SapoCustomerResponse.Customer();
        customer.setId(id);
        SapoCustomerResponse response = new SapoCustomerResponse();
        response.setCustomer(customer);
        return response;
    }

    private static User customerUser() {
        return User.builder()
                .id(1L)
                .email("anh@example.com")
                .fullName("Nguyen Anh")
                .phoneNumber("0900000000")
                .role(UserRole.CUSTOMER)
                .gender(Gender.FEMALE)
                .dateOfBirth(LocalDate.of(1995, 3, 20))
                .tier(CustomerTier.GOLD)
                .sapoSyncStatus(SapoSyncStatus.PENDING)
                .build();
    }

    @Test
    void pushCustomer_NeverSynced_CreatesCustomerAndStoresId() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerRequest.class))).thenAnswer(invocation -> {
            SapoCustomerRequest request = invocation.getArgument(0);
            assertThat(request.getCustomer().getFirstName()).isEqualTo("Nguyen");
            assertThat(request.getCustomer().getLastName()).isEqualTo("Anh");
            assertThat(request.getCustomer().getEmail()).isEqualTo("anh@example.com");
            assertThat(request.getCustomer().getPhone()).isEqualTo("0900000000");
            assertThat(request.getCustomer().getGender()).isEqualTo("Female");
            assertThat(request.getCustomer().getDob()).isEqualTo("1995-03-20");
            assertThat(request.getCustomer().getTags()).isEqualTo("fashionvista_tier_gold");
            return customerResponse("501");
        });
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoCustomerId()).isEqualTo(501L);
        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).updateCustomer(any(), any());
    }

    @Test
    void pushCustomer_AlreadySynced_CallsUpdateNotCreate() {
        User user = customerUser();
        user.setSapoCustomerId(501L);
        user.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.updateCustomer(eq(501L), any(SapoCustomerRequest.class)))
                .thenReturn(customerResponse("501"));
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).createCustomer(any());
        verify(sapoApiClient).updateCustomer(eq(501L), any(SapoCustomerRequest.class));
    }

    @Test
    void pushCustomer_UserNotFound_DoesNothing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        sapoCustomerSyncService.pushCustomer(99L);

        verify(userRepository, never()).save(any());
        verify(sapoApiClient, never()).createCustomer(any());
    }

    @Test
    void pushCustomer_NonCustomerRole_DoesNothing() {
        User admin = customerUser();
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        sapoCustomerSyncService.pushCustomer(1L);

        verify(userRepository, never()).save(any());
        verify(sapoApiClient, never()).createCustomer(any());
    }

    @Test
    void pushCustomer_ApiThrows_MarksFailedAndDoesNotRethrow() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerRequest.class)))
                .thenThrow(new RuntimeException("Sapo down"));
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
    }

    @Test
    void pushCustomer_NullResponse_MarksFailed() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerRequest.class))).thenReturn(null);
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(user.getSapoCustomerId()).isNull();
    }

    @Test
    void pushCustomer_IncompleteResponseMissingId_MarksFailed() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerRequest.class))).thenReturn(customerResponse(null));
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(user.getSapoCustomerId()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=SapoCustomerSyncServiceTest" -q
```
Expected: FAIL — compile error, `SapoCustomerSyncService` class does not exist.

- [ ] **Step 3: Create `SapoCustomerSyncService.java`**

```java
package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Gender;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerResponse;
import com.fashionvista.backend.integration.sapo.util.SapoNameSplitter;
import com.fashionvista.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoCustomerSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoCustomerSyncService.class);

    private final SapoApiClient sapoApiClient;
    private final UserRepository userRepository;

    @Async("sapoCustomerTaskExecutor")
    @Transactional
    public void pushCustomer(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Sapo customer sync: user id={} not found, skipping.", userId);
            return;
        }
        if (user.getRole() != UserRole.CUSTOMER) {
            log.warn("Sapo customer sync: user id={} has role={}, skipping.", userId, user.getRole());
            return;
        }
        doPush(user);
        userRepository.save(user);
    }

    private void doPush(User user) {
        try {
            SapoCustomerRequest request = buildCustomerRequest(user);
            SapoCustomerResponse response = user.getSapoCustomerId() == null
                    ? sapoApiClient.createCustomer(request)
                    : sapoApiClient.updateCustomer(user.getSapoCustomerId(), request);
            if (response == null || response.getCustomer() == null || response.getCustomer().getId() == null) {
                user.setSapoSyncStatus(SapoSyncStatus.FAILED);
                return;
            }
            user.setSapoCustomerId(Long.valueOf(response.getCustomer().getId()));
            user.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        } catch (RuntimeException ex) {
            log.error("Sapo customer sync failed for user id={}: {}", user.getId(), ex.getMessage(), ex);
            user.setSapoSyncStatus(SapoSyncStatus.FAILED);
        }
    }

    private SapoCustomerRequest buildCustomerRequest(User user) {
        SapoNameSplitter.Split name = SapoNameSplitter.splitLastName(user.getFullName());
        SapoCustomerRequest.Customer.CustomerBuilder customer = SapoCustomerRequest.Customer.builder()
                .firstName(name.getFirstName())
                .lastName(name.getLastName())
                .email(user.getEmail())
                .phone(user.getPhoneNumber())
                .gender(mapGender(user.getGender()))
                .dob(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null)
                .tags(user.getTier() != null ? "fashionvista_tier_" + user.getTier().name().toLowerCase() : null);

        return SapoCustomerRequest.builder().customer(customer.build()).build();
    }

    private String mapGender(Gender gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case MALE -> "Male";
            case FEMALE -> "Female";
            case OTHER -> "Other";
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=SapoCustomerSyncServiceTest" -q
```
Expected: PASS — all 7 tests green.

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/integration/sapo/service/SapoCustomerSyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoCustomerSyncServiceTest.java
git commit -m "feat(customer): add SapoCustomerSyncService"
```

---

### Task 7: Wire push into AuthServiceImpl.register()

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\service\impl\AuthServiceImpl.java`
- Modify: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\test\java\com\fashionvista\backend\service\impl\AuthServiceImplTest.java`

**Interfaces:**
- Consumes: `SapoCustomerSyncService.pushCustomer(Long)` (Task 6).
- Produces: `AuthServiceImpl.register()` now schedules a Sapo customer push after every successful registration commit — no new public methods (implements the existing `AuthService` interface unchanged).

- [ ] **Step 1: Add a failing mock + transaction-timing test to `AuthServiceImplTest.java`**

Add this import after the existing `import com.fashionvista.backend.entity.UserRole;` line (line 28):

```java
import com.fashionvista.backend.integration.sapo.service.SapoCustomerSyncService;
```

Add these imports after `import org.mockito.junit.jupiter.MockitoExtension;` (line 20):

```java
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

Add a new mock field after `private RefreshTokenRepository refreshTokenRepository;` (line 52):

```java
    @Mock
    private SapoCustomerSyncService sapoCustomerSyncService;
```

Add this test right after `register_ExistingEmail_ThrowsException` (after line 104, before `login_ValidCredentials_ReturnsAuthResponse`):

```java

    @Test
    void register_NewEmail_TriggersSapoCustomerPush() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        authService.register(request);

        verify(sapoCustomerSyncService).pushCustomer(1L);
    }

    @Test
    void register_WithinActiveTransaction_DefersCustomerPushUntilAfterCommit() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        TransactionSynchronizationManager.initSynchronization();
        try {
            authService.register(request);
            verify(sapoCustomerSyncService, never()).pushCustomer(anyLong());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(sapoCustomerSyncService).pushCustomer(1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
```

Add this static import after `import static org.mockito.ArgumentMatchers.any;` (line 7):

```java
import static org.mockito.ArgumentMatchers.anyLong;
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=AuthServiceImplTest" -q
```
Expected: FAIL — `register_NewEmail_TriggersSapoCustomerPush` and `register_WithinActiveTransaction_DefersCustomerPushUntilAfterCommit` fail because `register()` never calls `pushCustomer`.

- [ ] **Step 3: Wire `SapoCustomerSyncService` into `AuthServiceImpl.java`**

Add this import after `import com.fashionvista.backend.entity.UserRole;` (line 22):

```java
import com.fashionvista.backend.integration.sapo.service.SapoCustomerSyncService;
```

Add these imports after `import com.fashionvista.backend.service.TokenBlacklistService;` (line 33):

```java
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

Replace the field declaration (line 54):

```java
    private final LoginRateLimitService loginRateLimitService;
```

with:

```java
    private final LoginRateLimitService loginRateLimitService;
    private final SapoCustomerSyncService sapoCustomerSyncService;
```

Replace the body of `register()` (lines 93-102):

```java
        User saved = userRepository.save(user);
        String verificationToken = generateVerificationToken();
        EmailVerificationToken tokenEntity = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(saved)
                .build();
        emailVerificationTokenRepository.save(tokenEntity);
        emailService.sendVerificationEmail(saved, verificationToken);

        return buildAuthResponse(saved, null);
    }
```

with:

```java
        User saved = userRepository.save(user);
        scheduleCustomerPushAfterCommit(saved.getId());

        String verificationToken = generateVerificationToken();
        EmailVerificationToken tokenEntity = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(saved)
                .build();
        emailVerificationTokenRepository.save(tokenEntity);
        emailService.sendVerificationEmail(saved, verificationToken);

        return buildAuthResponse(saved, null);
    }

    private void scheduleCustomerPushAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sapoCustomerSyncService.pushCustomer(userId);
                }
            });
        } else {
            sapoCustomerSyncService.pushCustomer(userId);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=AuthServiceImplTest" -q
```
Expected: PASS — all tests green (existing tests plus the 2 new ones).

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/service/impl/AuthServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/AuthServiceImplTest.java
git commit -m "feat(customer): push new customers to Sapo after registration commit"
```

---

### Task 8: Wire push into UserController.updateProfile()

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\main\java\com\fashionvista\backend\controller\UserController.java`
- Create: `D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync\src\test\java\com\fashionvista\backend\controller\UserControllerTest.java`

**Interfaces:**
- Consumes: `SapoCustomerSyncService.pushCustomer(Long)` (Task 6).
- Produces: `UserController.updateProfile()` now calls `pushCustomer()` directly (no defer wrapper) after saving the updated profile — no new public methods (implements the existing REST contract unchanged).

`UserControllerTest.java` does not exist yet in this codebase — it is created from scratch in this task, covering only `updateProfile()` (the method this task changes), following the same direct-instantiation `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` pattern used by every other service/controller test in the codebase.

- [ ] **Step 1: Write the failing test file**

```java
package com.fashionvista.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.UpdateProfileRequest;
import com.fashionvista.backend.dto.UserProfileResponse;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.service.SapoCustomerSyncService;
import com.fashionvista.backend.repository.AddressRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.RefreshTokenService;
import com.fashionvista.backend.service.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserContextService userContextService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SapoCustomerSyncService sapoCustomerSyncService;

    @InjectMocks
    private UserController userController;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Old Name")
                .phoneNumber("0900000000")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    @Test
    void updateProfile_ValidRequest_SavesAndTriggersSapoCustomerPushDirectly() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("New Name");
        request.setPhoneNumber("0911111111");

        when(userContextService.getCurrentUser()).thenReturn(user);
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserProfileResponse response = userController.updateProfile(request);

        assertEquals("New Name", response.getFullName());
        verify(sapoCustomerSyncService).pushCustomer(1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=UserControllerTest" -q
```
Expected: FAIL — compile error (`SapoCustomerSyncService` not injectable into `UserController`) and `pushCustomer` never called.

- [ ] **Step 3: Wire `SapoCustomerSyncService` into `UserController.java`**

Add this import after `import com.fashionvista.backend.entity.Address;` (line 8):

```java
import com.fashionvista.backend.integration.sapo.service.SapoCustomerSyncService;
```

Replace the field declaration (line 37):

```java
    private final RefreshTokenService refreshTokenService;
```

with:

```java
    private final RefreshTokenService refreshTokenService;
    private final SapoCustomerSyncService sapoCustomerSyncService;
```

Replace `updateProfile()` (lines 52-66):

```java
    @PutMapping
    public UserProfileResponse updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        var user = userContextService.getCurrentUser();
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        var saved = userRepository.save(user);
        return UserProfileResponse.builder()
            .id(saved.getId())
            .email(saved.getEmail())
            .fullName(saved.getFullName())
            .phoneNumber(saved.getPhoneNumber())
            .role(saved.getRole().name())
            .active(saved.isActive())
            .build();
    }
```

with:

```java
    @PutMapping
    public UserProfileResponse updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        var user = userContextService.getCurrentUser();
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        var saved = userRepository.save(user);
        sapoCustomerSyncService.pushCustomer(saved.getId());
        return UserProfileResponse.builder()
            .id(saved.getId())
            .email(saved.getEmail())
            .fullName(saved.getFullName())
            .phoneNumber(saved.getPhoneNumber())
            .role(saved.getRole().name())
            .active(saved.isActive())
            .build();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test "-Dtest=UserControllerTest" -q
```
Expected: PASS.

- [ ] **Step 5: Run the full test suite**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
./mvnw test -q
```
Expected: `BUILD SUCCESS`, 0 failures — confirms nothing else in the codebase broke from the new `SapoCustomerSyncService` constructor dependency added to `AuthServiceImpl` and `UserController`.

- [ ] **Step 6: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend\.claude\worktrees\sapo-customer-sync
git add src/main/java/com/fashionvista/backend/controller/UserController.java src/test/java/com/fashionvista/backend/controller/UserControllerTest.java
git commit -m "feat(customer): push updated customers to Sapo directly on profile update"
```

---

## Self-Review

**Spec coverage:**
- Data Model (`sapo_customer_id`, `sapo_sync_status` on `User`) → Task 1.
- Field Mapping (name split, gender, dob, tags, role filter) → Task 2 (name split), Task 6 (`buildCustomerRequest`/`mapGender`, role filter in `pushCustomer`).
- Sapo API paths (`POST /admin/customers.json`, `PUT /admin/customers/{id}.json`) → Task 4.
- Sync Flow on registration (after-commit scheduling, before verification email) → Task 7.
- Sync Flow on profile update (direct call, no defer wrapper) → Task 8.
- Testing section's 6 required `SapoCustomerSyncServiceTest` cases (create, update, not-found, non-customer, API throws, null/incomplete response) → Task 6, all present.
- `SapoNameSplitterTest` 3 required cases → Task 2.
- `AuthServiceImplTest` transaction-timing test → Task 7.
- `UserControllerTest` direct-call assertion → Task 8.
- Non-goals (no `SyncDomain.CUSTOMER`, no sync-health, no address sync, no ban integration) → deliberately absent from every task; called out in Global Constraints.

**Placeholder scan:** No TBD/TODO markers; every step has runnable code; no "similar to Task N" shortcuts — Task 7 and Task 8 each spell out their full diff independently.

**Type consistency:** `SapoCustomerSyncService.pushCustomer(Long)` signature (Task 6) matches every call site (`AuthServiceImpl.scheduleCustomerPushAfterCommit`, `UserController.updateProfile`, both in Task 7/8). `SapoNameSplitter.splitLastName(String): Split` (Task 2) matches its only call site in Task 6's `buildCustomerRequest`. `SapoCustomerRequest.Customer`/`SapoCustomerResponse.Customer` field names (Task 3) match usage in `SapoApiClient` (Task 4, opaque passthrough) and `SapoCustomerSyncService` (Task 6: `firstName/lastName/email/phone/gender/dob/tags` on the request builder, `getId()` on the response).

---
