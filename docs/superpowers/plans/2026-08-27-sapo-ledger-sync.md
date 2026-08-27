# Sapo Ledger Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push FashionVista `Payment` and `Refund` events to Sapo as Transactions (`kind=sale` on payment success, `kind=refund` on refund creation) so Sapo's ledger reflects real money movement, following the existing async push + scheduled-retry pattern already used for Order and Shipping sync.

**Architecture:** A new `SapoLedgerSyncService` exposes two `@Async` entry points (`pushPaymentTransaction(Long paymentId)`, `pushRefundTransaction(Long refundId)`) invoked directly (no `afterCommit` wrapper — matching `AdminOrderServiceImpl.pushOrder`'s convention) from the three call sites that transition a payment to `PAID` or create a `Refund`. Both entry points re-fetch their entity, skip with a WARN log if the parent `Order` has no `sapoOrderId` yet, build a `SapoTransactionRequest`, call `SapoApiClient.createTransaction`, and record success/failure on 4 new Sapo-tracking columns. A `@Scheduled` method retries rows left in `FAILED` state with no `sapoTransactionId`.

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring `RestClient`, Spring Data JPA, Flyway, Lombok, Jackson, JUnit 5, Mockito, AssertJ, `MockRestServiceServer`.

**Spec:** `docs/superpowers/specs/2026-08-27-sapo-ledger-sync-design.md`

## Global Constraints

- Outbound-only: no Sapo webhook handling (`order_transactions/create`, `refunds/create` are explicitly out of scope).
- No `kind=authorization`/`capture`/`void` — only `kind=sale` (payment success) and `kind=refund` (refund creation).
- No push for failed or pending payment attempts — only on the `PAID` transition.
- No automatic refund-on-cancel and no `parent_id` lineage tracking.
- Reuse the existing `SapoSyncStatus` enum (`PENDING`, `SYNCED`, `FAILED`) — do not add a new enum.
- Follow the `sapo<Domain>Id` / `SyncStatus` / `SyncError` / `SyncedAt` naming convention already established on `Order`.
- Direct-call pattern at all three trigger sites (no `afterCommitOrNow` wrapper) — the target sync methods are themselves `@Async`, matching `AdminOrderServiceImpl.pushOrder`'s existing convention.
- Sapo sync errors are non-fatal: catch `RuntimeException`, record `FAILED` + truncated message (max 500 chars, `"Unknown error"` fallback for a null message), log ERROR, never rethrow.
- Never read or write `.env` files.
- Migration must be `V13__add_sapo_transaction_fields_to_payments_and_refunds.sql` — **correction from the spec's provisional `V14`**: this worktree's actual `src/main/resources/db/migration/` directory shows `V12__add_sapo_fulfillment_fields_to_orders.sql` (Shipping) as the current highest version, so Ledger's migration is `V13`, not `V14`.

---

## File Structure

**New files:**
- `src/main/resources/db/migration/V13__add_sapo_transaction_fields_to_payments_and_refunds.sql` — adds 4 nullable columns to both `payments` and `refunds`.
- `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionRequest.java` — outbound request DTO, wraps a nested `Transaction` under the `"transaction"` JSON key (Sapo's resource-wrapping convention).
- `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionResponse.java` — inbound response DTO, same wrapping convention, only the `id` field is consumed.
- `src/main/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncService.java` — the async push/retry service for both `Payment` and `Refund`.
- `src/test/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncServiceTest.java`

**Modified files:**
- `src/main/java/com/fashionvista/backend/entity/Payment.java` — add 4 Sapo-tracking fields.
- `src/main/java/com/fashionvista/backend/entity/Refund.java` — add 4 Sapo-tracking fields.
- `src/main/java/com/fashionvista/backend/repository/PaymentRepository.java` — add `findBySapoSyncStatusAndSapoTransactionIdIsNull`.
- `src/main/java/com/fashionvista/backend/repository/RefundRepository.java` — add `findBySapoSyncStatusAndSapoTransactionIdIsNull`.
- `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java` — add `createTransaction(String, SapoTransactionRequest)`.
- `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java` — add `createTransaction` coverage.
- `src/main/java/com/fashionvista/backend/config/AsyncConfig.java` — add `sapoLedgerTaskExecutor` bean.
- `src/main/java/com/fashionvista/backend/controller/VnPayController.java` — call `pushPaymentTransaction` after a successful VNPay result.
- `src/main/java/com/fashionvista/backend/service/impl/AdminPaymentServiceImpl.java` — call `pushPaymentTransaction` at all three real `PAID`-transition points (`updatePaymentStatus`, and both loops of `syncCodDeliveredPayments`).
- `src/main/java/com/fashionvista/backend/service/impl/AdminOrderServiceImpl.java` — call `pushRefundTransaction` in `createPartialRefund`.

**Note on `AdminPaymentServiceImpl` scope:** the spec's summary says "admin/COD confirm" triggers the push, but the file has no single method with that name. Reading it shows two independent methods that both transition a `Payment` to `PAID` in production: `updatePaymentStatus(Long, PaymentStatus)` (generic — any target status, must be guarded) and `syncCodDeliveredPayments()` (COD-specific bulk job, two separate loops that each flip a payment to `PAID`). All three are genuine `PAID`-transition points and none is redundant with another, so this plan hooks all three.

---

## Task 1: Sapo Transaction Migration + Entity Fields

**Files:**
- Create: `src/main/resources/db/migration/V13__add_sapo_transaction_fields_to_payments_and_refunds.sql`
- Modify: `src/main/java/com/fashionvista/backend/entity/Payment.java`
- Modify: `src/main/java/com/fashionvista/backend/entity/Refund.java`
- Test: `src/test/java/com/fashionvista/backend/entity/PaymentTest.java` (new)
- Test: `src/test/java/com/fashionvista/backend/entity/RefundTest.java` (new)

**Interfaces:**
- Consumes: existing `SapoSyncStatus` enum (`PENDING`, `SYNCED`, `FAILED`) from `com.fashionvista.backend.entity`.
- Produces: `Payment.getSapoTransactionId()/setSapoTransactionId(String)`, `Payment.getSapoSyncStatus()/setSapoSyncStatus(SapoSyncStatus)`, `Payment.getSapoSyncError()/setSapoSyncError(String)`, `Payment.getSapoSyncedAt()/setSapoSyncedAt(LocalDateTime)` — and the identical four accessors on `Refund`. Later tasks (repositories, `SapoLedgerSyncService`) depend on these exact names.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fashionvista/backend/entity/PaymentTest.java`:
```java
package com.fashionvista.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void sapoFields_DefaultToNull() {
        Payment payment = new Payment();

        assertThat(payment.getSapoTransactionId()).isNull();
        assertThat(payment.getSapoSyncStatus()).isNull();
        assertThat(payment.getSapoSyncError()).isNull();
        assertThat(payment.getSapoSyncedAt()).isNull();
    }

    @Test
    void sapoFields_SetAndGetRoundTrip() {
        Payment payment = new Payment();
        LocalDateTime now = LocalDateTime.now();

        payment.setSapoTransactionId("321");
        payment.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        payment.setSapoSyncError("boom");
        payment.setSapoSyncedAt(now);

        assertThat(payment.getSapoTransactionId()).isEqualTo("321");
        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(payment.getSapoSyncError()).isEqualTo("boom");
        assertThat(payment.getSapoSyncedAt()).isEqualTo(now);
    }
}
```

`src/test/java/com/fashionvista/backend/entity/RefundTest.java`:
```java
package com.fashionvista.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefundTest {

    @Test
    void sapoFields_DefaultToNull() {
        Refund refund = new Refund();

        assertThat(refund.getSapoTransactionId()).isNull();
        assertThat(refund.getSapoSyncStatus()).isNull();
        assertThat(refund.getSapoSyncError()).isNull();
        assertThat(refund.getSapoSyncedAt()).isNull();
    }

    @Test
    void sapoFields_SetAndGetRoundTrip() {
        Refund refund = new Refund();
        LocalDateTime now = LocalDateTime.now();

        refund.setSapoTransactionId("654");
        refund.setSapoSyncStatus(SapoSyncStatus.FAILED);
        refund.setSapoSyncError("boom");
        refund.setSapoSyncedAt(now);

        assertThat(refund.getSapoTransactionId()).isEqualTo("654");
        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(refund.getSapoSyncError()).isEqualTo("boom");
        assertThat(refund.getSapoSyncedAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=PaymentTest,RefundTest`
Expected: COMPILE FAILURE — `cannot find symbol: method getSapoTransactionId()` (and similarly for the other 3 accessors on both classes).

- [ ] **Step 3: Add the 4 fields to `Payment.java`**

Insert into `src/main/java/com/fashionvista/backend/entity/Payment.java`, right after the existing `refundAmount` field (before the `createdAt` field):

```java
    @Column(name = "sapo_transaction_id", length = 64)
    private String sapoTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_sync_status")
    private SapoSyncStatus sapoSyncStatus;

    @Column(name = "sapo_sync_error", length = 500)
    private String sapoSyncError;

    @Column(name = "sapo_synced_at")
    private LocalDateTime sapoSyncedAt;
```

(`EnumType` and `Enumerated` are already imported in this file for `paymentMethod`/`paymentStatus`; no new imports needed.)

- [ ] **Step 4: Add the same 4 fields to `Refund.java`**

Insert into `src/main/java/com/fashionvista/backend/entity/Refund.java`, right after the existing `refundedBy` field (before the `createdAt` field):

```java
    @Column(name = "sapo_transaction_id", length = 64)
    private String sapoTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_sync_status")
    private SapoSyncStatus sapoSyncStatus;

    @Column(name = "sapo_sync_error", length = 500)
    private String sapoSyncError;

    @Column(name = "sapo_synced_at")
    private LocalDateTime sapoSyncedAt;
```

This file does not yet import `EnumType`/`Enumerated` — add these two imports alongside the existing `jakarta.persistence.*` imports:
```java
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
```

- [ ] **Step 5: Write the migration**

`src/main/resources/db/migration/V13__add_sapo_transaction_fields_to_payments_and_refunds.sql`:
```sql
-- Migration: Add Sapo Transaction sync fields to payments and refunds
-- Date: 2026-08-27
-- Purpose: Support outbound Sapo Transaction sync for the Ledger domain -
--   push kind=sale on payment success and kind=refund on refund creation.

-- Payments: transaction id/status/error/synced-at for the kind=sale push
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_transaction_id VARCHAR(64) DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_sync_status VARCHAR(20) DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_sync_error VARCHAR(500) DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_synced_at TIMESTAMP DEFAULT NULL;

-- Refunds: transaction id/status/error/synced-at for the kind=refund push
ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_transaction_id VARCHAR(64) DEFAULT NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_sync_status VARCHAR(20) DEFAULT NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_sync_error VARCHAR(500) DEFAULT NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_synced_at TIMESTAMP DEFAULT NULL;
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./mvnw test -Dtest=PaymentTest,RefundTest`
Expected: PASS (4 tests, 0 failures). Flyway also validates the new migration against the test H2 database on the same run — a bad SQL statement fails the whole test suite, not just these two classes.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V13__add_sapo_transaction_fields_to_payments_and_refunds.sql src/main/java/com/fashionvista/backend/entity/Payment.java src/main/java/com/fashionvista/backend/entity/Refund.java src/test/java/com/fashionvista/backend/entity/PaymentTest.java src/test/java/com/fashionvista/backend/entity/RefundTest.java
git commit -m "feat(ledger): add Sapo transaction sync fields to Payment and Refund"
```

---

## Task 2: Repository Query Methods

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/repository/PaymentRepository.java`
- Modify: `src/main/java/com/fashionvista/backend/repository/RefundRepository.java`
- Test: `src/test/java/com/fashionvista/backend/repository/PaymentRepositoryTest.java` (new)
- Test: `src/test/java/com/fashionvista/backend/repository/RefundRepositoryTest.java` (new)

**Interfaces:**
- Consumes: `Payment.sapoSyncStatus`/`sapoTransactionId` and `Refund.sapoSyncStatus`/`sapoTransactionId` from Task 1; `SapoSyncStatus` enum.
- Produces: `PaymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus)` returning `List<Payment>`; `RefundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus)` returning `List<Refund>`. `SapoLedgerSyncService.retryFailedTransactions()` (Task 6) calls both.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fashionvista/backend/repository/PaymentRepositoryTest.java`:
```java
package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrderRepository orderRepository;

    private Payment persistPayment(SapoSyncStatus sapoSyncStatus, String sapoTransactionId) {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-" + System.nanoTime())
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(BigDecimal.TEN)
                .build());
        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .amount(BigDecimal.TEN)
                .sapoSyncStatus(sapoSyncStatus)
                .sapoTransactionId(sapoTransactionId)
                .build();
        return paymentRepository.save(payment);
    }

    @Test
    void findBySapoSyncStatusAndSapoTransactionIdIsNull_ReturnsOnlyUnsyncedFailedRows() {
        Payment failedUnsynced = persistPayment(SapoSyncStatus.FAILED, null);
        persistPayment(SapoSyncStatus.FAILED, "already-999");
        persistPayment(SapoSyncStatus.SYNCED, "111");
        persistPayment(SapoSyncStatus.PENDING, null);

        var result = paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED);

        assertThat(result).extracting(Payment::getId).containsExactly(failedUnsynced.getId());
    }
}
```

`src/test/java/com/fashionvista/backend/repository/RefundRepositoryTest.java`:
```java
package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.RefundMethod;
import com.fashionvista.backend.entity.SapoSyncStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RefundRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Refund persistRefund(SapoSyncStatus sapoSyncStatus, String sapoTransactionId) {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-" + System.nanoTime())
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.REFUND_PENDING)
                .totalAmount(BigDecimal.TEN)
                .build());
        Refund refund = Refund.builder()
                .order(order)
                .amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.ORIGINAL)
                .sapoSyncStatus(sapoSyncStatus)
                .sapoTransactionId(sapoTransactionId)
                .build();
        return refundRepository.save(refund);
    }

    @Test
    void findBySapoSyncStatusAndSapoTransactionIdIsNull_ReturnsOnlyUnsyncedFailedRows() {
        Refund failedUnsynced = persistRefund(SapoSyncStatus.FAILED, null);
        persistRefund(SapoSyncStatus.FAILED, "already-999");
        persistRefund(SapoSyncStatus.SYNCED, "222");

        var result = refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED);

        assertThat(result).extracting(Refund::getId).containsExactly(failedUnsynced.getId());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=PaymentRepositoryTest,RefundRepositoryTest`
Expected: COMPILE FAILURE — `cannot find symbol: method findBySapoSyncStatusAndSapoTransactionIdIsNull`.

- [ ] **Step 3: Add the query method to `PaymentRepository.java`**

```java
package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.SapoSyncStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByOrder(Order order);

    List<Payment> findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus sapoSyncStatus);
}
```

- [ ] **Step 4: Add the query method to `RefundRepository.java`**

```java
package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.SapoSyncStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Refund> findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus sapoSyncStatus);
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest=PaymentRepositoryTest,RefundRepositoryTest`
Expected: PASS (2 tests, 0 failures).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/repository/PaymentRepository.java src/main/java/com/fashionvista/backend/repository/RefundRepository.java src/test/java/com/fashionvista/backend/repository/PaymentRepositoryTest.java src/test/java/com/fashionvista/backend/repository/RefundRepositoryTest.java
git commit -m "feat(ledger): add findBySapoSyncStatusAndSapoTransactionIdIsNull queries"
```

---

## Task 3: SapoTransactionRequest / SapoTransactionResponse DTOs

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionRequest.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionResponse.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionDtoTest.java` (new)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `SapoTransactionRequest.builder().transaction(SapoTransactionRequest.Transaction.builder().amount(BigDecimal).kind(String).gateway(String).currency(String).status(String).build()).build()`; `SapoTransactionResponse.getTransaction().getId()` returning `String`. `SapoApiClient.createTransaction` (Task 4) and `SapoLedgerSyncService` (Task 6) both depend on these exact shapes.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionDtoTest.java`:
```java
package com.fashionvista.backend.integration.sapo.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SapoTransactionDtoTest {

    @Test
    void request_SerializesToTransactionWrappedSnakeCaseJson() throws Exception {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(new BigDecimal("150000"))
                .kind("sale")
                .gateway("VNPay")
                .currency("VND")
                .status("success")
                .build();
        SapoTransactionRequest request = SapoTransactionRequest.builder().transaction(transaction).build();

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"transaction\"");
        assertThat(json).contains("\"amount\":150000");
        assertThat(json).contains("\"kind\":\"sale\"");
        assertThat(json).contains("\"gateway\":\"VNPay\"");
        assertThat(json).contains("\"currency\":\"VND\"");
        assertThat(json).contains("\"status\":\"success\"");
    }

    @Test
    void response_DeserializesIdFromNestedTransactionObject() throws Exception {
        String json = "{\"transaction\":{\"id\":\"321\",\"order_id\":\"555\",\"unexpected_field\":true}}";

        SapoTransactionResponse response = new ObjectMapper().readValue(json, SapoTransactionResponse.class);

        assertThat(response.getTransaction().getId()).isEqualTo("321");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoTransactionDtoTest`
Expected: COMPILE FAILURE — `cannot find symbol: class SapoTransactionRequest`.

- [ ] **Step 3: Write `SapoTransactionRequest.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoTransactionRequest {

    Transaction transaction;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Transaction {
        BigDecimal amount;
        String kind;
        String gateway;
        String currency;
        String status;
    }
}
```

- [ ] **Step 4: Write `SapoTransactionResponse.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoTransactionResponse {

    private Transaction transaction;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transaction {
        private String id;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoTransactionDtoTest`
Expected: PASS (2 tests, 0 failures).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionRequest.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionResponse.java src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoTransactionDtoTest.java
git commit -m "feat(ledger): add SapoTransactionRequest/Response DTOs"
```

---

## Task 4: SapoApiClient.createTransaction

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java`

**Interfaces:**
- Consumes: `SapoTransactionRequest`/`SapoTransactionResponse` from Task 3.
- Produces: `SapoApiClient.createTransaction(String sapoOrderId, SapoTransactionRequest request)` returning `SapoTransactionResponse`. `SapoLedgerSyncService` (Task 6) calls this exact signature.

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java` — new imports first:
```java
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import java.math.BigDecimal;
```

New private helper (add alongside `sampleFulfillmentRequest()`):
```java
    private SapoTransactionRequest sampleTransactionRequest() {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(new BigDecimal("150000"))
                .kind("sale")
                .gateway("VNPay")
                .currency("VND")
                .status("success")
                .build();
        return SapoTransactionRequest.builder().transaction(transaction).build();
    }
```

New test (add alongside `cancelFulfillment_PostsToCancelJsonAndParsesResponse`):
```java
    @Test
    void createTransaction_PostsToOrderTransactionsJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/transactions.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"transaction\":{\"id\":\"321\"}}", MediaType.APPLICATION_JSON));

        SapoTransactionResponse response = client.createTransaction("555", sampleTransactionRequest());

        server.verify();
        assertEquals("321", response.getTransaction().getId());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoApiClientTest#createTransaction_PostsToOrderTransactionsJsonAndParsesResponse`
Expected: COMPILE FAILURE — `cannot find symbol: method createTransaction`.

- [ ] **Step 3: Implement `createTransaction` in `SapoApiClient.java`**

Add these two imports:
```java
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
```

Add the method after `cancelFulfillment`:
```java
    public SapoTransactionResponse createTransaction(String sapoOrderId, SapoTransactionRequest request) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/transactions.json", sapoOrderId)
                .body(request)
                .retrieve()
                .body(SapoTransactionResponse.class);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoApiClientTest`
Expected: PASS (all `SapoApiClientTest` tests, 0 failures).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java
git commit -m "feat(ledger): add SapoApiClient.createTransaction"
```

---

## Task 5: sapoLedgerTaskExecutor Bean

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/config/AsyncConfig.java`
- Test: `src/test/java/com/fashionvista/backend/config/AsyncConfigTest.java` (new)

**Interfaces:**
- Consumes: nothing.
- Produces: a Spring bean named `"sapoLedgerTaskExecutor"` of type `Executor`. `SapoLedgerSyncService` (Task 6) references it by this exact name in `@Async("sapoLedgerTaskExecutor")`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/fashionvista/backend/config/AsyncConfigTest.java`:
```java
package com.fashionvista.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void sapoLedgerTaskExecutor_IsConfiguredWithExpectedPoolSizesAndPrefix() {
        AsyncConfig config = new AsyncConfig();

        Executor executor = config.sapoLedgerTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(threadPoolTaskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(threadPoolTaskExecutor.getMaxPoolSize()).isEqualTo(5);
        assertThat(threadPoolTaskExecutor.getThreadNamePrefix()).isEqualTo("sapo-ledger-");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AsyncConfigTest`
Expected: COMPILE FAILURE — `cannot find symbol: method sapoLedgerTaskExecutor()`.

- [ ] **Step 3: Add the bean to `AsyncConfig.java`**

Add after `sapoShippingTaskExecutor()`, before the closing brace of the class:
```java
    @Bean(name = "sapoLedgerTaskExecutor")
    public Executor sapoLedgerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sapo-ledger-");
        executor.initialize();
        return executor;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AsyncConfigTest`
Expected: PASS (1 test, 0 failures).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/config/AsyncConfig.java src/test/java/com/fashionvista/backend/config/AsyncConfigTest.java
git commit -m "feat(ledger): add sapoLedgerTaskExecutor async bean"
```

---

## Task 6: SapoLedgerSyncService

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncService.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncServiceTest.java`

**Interfaces:**
- Consumes: `SapoApiClient.createTransaction(String, SapoTransactionRequest)` (Task 4); `PaymentRepository`/`RefundRepository` incl. `findBySapoSyncStatusAndSapoTransactionIdIsNull` (Task 2); `Payment`/`Refund` Sapo fields (Task 1); `SapoTransactionRequest`/`Response` (Task 3); `Order.getSapoOrderId()`, `Order.getPaymentMethod()` (existing); `PaymentMethod` enum values `COD`/`BANK_TRANSFER`/`VNPAY`/`MOMO` (existing).
- Produces: `SapoLedgerSyncService.pushPaymentTransaction(Long paymentId)`, `SapoLedgerSyncService.pushRefundTransaction(Long refundId)`, `SapoLedgerSyncService.retryFailedTransactions()`. Tasks 7, 8, 9 call the first two by these exact signatures.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncServiceTest.java`:
```java
package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.RefundMethod;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.RefundRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoLedgerSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @InjectMocks
    private SapoLedgerSyncService sapoLedgerSyncService;

    private Order orderWithSapoId(PaymentMethod paymentMethod) {
        return Order.builder().id(1L).sapoOrderId("555").paymentMethod(paymentMethod).build();
    }

    private SapoTransactionResponse responseWithId(String id) {
        SapoTransactionResponse response = new SapoTransactionResponse();
        SapoTransactionResponse.Transaction transaction = new SapoTransactionResponse.Transaction();
        transaction.setId(id);
        response.setTransaction(transaction);
        return response;
    }

    @Test
    void pushPaymentTransaction_SuccessfulCreate_SetsTransactionIdAndSynced() {
        Order order = orderWithSapoId(PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(10L).order(order).paymentMethod(PaymentMethod.VNPAY)
                .amount(new BigDecimal("150000")).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("321"));

        sapoLedgerSyncService.pushPaymentTransaction(10L);

        assertThat(payment.getSapoTransactionId()).isEqualTo("321");
        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(payment.getSapoSyncError()).isNull();
        assertThat(payment.getSapoSyncedAt()).isNotNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void pushPaymentTransaction_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(11L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(11L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException("Sapo down"));

        sapoLedgerSyncService.pushPaymentTransaction(11L);

        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(payment.getSapoSyncError()).isEqualTo("Sapo down");
        verify(paymentRepository).save(payment);
    }

    @Test
    void pushPaymentTransaction_ClientThrowsWithNullMessage_SetsFailedWithFallbackMessage() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(12L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(12L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException((String) null));

        sapoLedgerSyncService.pushPaymentTransaction(12L);

        assertThat(payment.getSapoSyncError()).isEqualTo("Unknown error");
    }

    @Test
    void pushPaymentTransaction_ClientThrowsWithOverlongMessage_TruncatesTo500Chars() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(13L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(13L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException("x".repeat(600)));

        sapoLedgerSyncService.pushPaymentTransaction(13L);

        assertThat(payment.getSapoSyncError()).hasSize(500);
    }

    @Test
    void pushPaymentTransaction_OrderNotYetSyncedToSapo_SkipsWithoutClientCall() {
        Order order = Order.builder().id(2L).sapoOrderId(null).paymentMethod(PaymentMethod.COD).build();
        Payment payment = Payment.builder().id(14L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(14L)).thenReturn(Optional.of(payment));

        sapoLedgerSyncService.pushPaymentTransaction(14L);

        verify(sapoApiClient, never()).createTransaction(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pushPaymentTransaction_PaymentNotFound_SkipsWithoutClientCall() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        sapoLedgerSyncService.pushPaymentTransaction(999L);

        verify(sapoApiClient, never()).createTransaction(any(), any());
    }

    @Test
    void pushRefundTransaction_SuccessfulCreate_SetsTransactionIdAndSynced() {
        Order order = orderWithSapoId(PaymentMethod.MOMO);
        Refund refund = Refund.builder().id(20L).order(order).amount(new BigDecimal("50000"))
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(refundRepository.findById(20L)).thenReturn(Optional.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("654"));

        sapoLedgerSyncService.pushRefundTransaction(20L);

        assertThat(refund.getSapoTransactionId()).isEqualTo("654");
        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(refund.getSapoSyncedAt()).isNotNull();
        verify(refundRepository).save(refund);
    }

    @Test
    void pushRefundTransaction_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = orderWithSapoId(PaymentMethod.BANK_TRANSFER);
        Refund refund = Refund.builder().id(21L).order(order).amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.MANUAL_CASH).build();
        when(refundRepository.findById(21L)).thenReturn(Optional.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException("Sapo down"));

        sapoLedgerSyncService.pushRefundTransaction(21L);

        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(refund.getSapoSyncError()).isEqualTo("Sapo down");
        verify(refundRepository).save(refund);
    }

    @Test
    void pushRefundTransaction_OrderNotYetSyncedToSapo_SkipsWithoutClientCall() {
        Order order = Order.builder().id(3L).sapoOrderId(null).paymentMethod(PaymentMethod.COD).build();
        Refund refund = Refund.builder().id(22L).order(order).amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(refundRepository.findById(22L)).thenReturn(Optional.of(refund));

        sapoLedgerSyncService.pushRefundTransaction(22L);

        verify(sapoApiClient, never()).createTransaction(any(), any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void retryFailedTransactions_RetriesFailedPaymentsAndRefundsWithNullTransactionId() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(30L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        Refund refund = Refund.builder().id(31L).order(order).amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.ORIGINAL).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        when(paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of(payment));
        when(refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any()))
                .thenReturn(responseWithId("321"))
                .thenReturn(responseWithId("654"));

        sapoLedgerSyncService.retryFailedTransactions();

        verify(sapoApiClient, times(2)).createTransaction(eq("555"), any());
        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
    }

    @ParameterizedTest
    @CsvSource({
        "COD,Cash on Delivery",
        "BANK_TRANSFER,Bank Transfer",
        "VNPAY,VNPay",
        "MOMO,MoMo"
    })
    void pushPaymentTransaction_MapsEveryPaymentMethodToItsSapoGateway(PaymentMethod paymentMethod, String expectedGateway) {
        Order order = orderWithSapoId(paymentMethod);
        Payment payment = Payment.builder().id(40L).order(order).paymentMethod(paymentMethod)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(40L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("321"));
        ArgumentCaptor<SapoTransactionRequest> captor = ArgumentCaptor.forClass(SapoTransactionRequest.class);

        sapoLedgerSyncService.pushPaymentTransaction(40L);

        verify(sapoApiClient).createTransaction(eq("555"), captor.capture());
        assertThat(captor.getValue().getTransaction().getGateway()).isEqualTo(expectedGateway);
        assertThat(captor.getValue().getTransaction().getKind()).isEqualTo("sale");
        assertThat(captor.getValue().getTransaction().getCurrency()).isEqualTo("VND");
        assertThat(captor.getValue().getTransaction().getStatus()).isEqualTo("success");
    }

    @Test
    void pushRefundTransaction_RequestUsesKindRefundAndOrdersPaymentMethodForGateway() {
        Order order = orderWithSapoId(PaymentMethod.VNPAY);
        Refund refund = Refund.builder().id(23L).order(order).amount(new BigDecimal("25000"))
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(refundRepository.findById(23L)).thenReturn(Optional.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("654"));
        ArgumentCaptor<SapoTransactionRequest> captor = ArgumentCaptor.forClass(SapoTransactionRequest.class);

        sapoLedgerSyncService.pushRefundTransaction(23L);

        verify(sapoApiClient).createTransaction(eq("555"), captor.capture());
        assertThat(captor.getValue().getTransaction().getKind()).isEqualTo("refund");
        assertThat(captor.getValue().getTransaction().getGateway()).isEqualTo("VNPay");
        assertThat(captor.getValue().getTransaction().getAmount()).isEqualByComparingTo("25000");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=SapoLedgerSyncServiceTest`
Expected: COMPILE FAILURE — `cannot find symbol: class SapoLedgerSyncService`.

- [ ] **Step 3: Implement `SapoLedgerSyncService.java`**

```java
package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.RefundRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SapoLedgerSyncService {

    private static final int SAPO_SYNC_ERROR_MAX_LENGTH = 500;

    private final SapoApiClient sapoApiClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Async("sapoLedgerTaskExecutor")
    @Transactional
    public void pushPaymentTransaction(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("Sapo ledger sync skipped: payment {} not found", paymentId);
            return;
        }
        doPushPayment(payment);
    }

    @Async("sapoLedgerTaskExecutor")
    @Transactional
    public void pushRefundTransaction(Long refundId) {
        Refund refund = refundRepository.findById(refundId).orElse(null);
        if (refund == null) {
            log.warn("Sapo ledger sync skipped: refund {} not found", refundId);
            return;
        }
        doPushRefund(refund);
    }

    @Scheduled(cron = "0 45 * * * ?")
    @Transactional
    public void retryFailedTransactions() {
        for (Payment payment : paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED)) {
            doPushPayment(payment);
        }
        for (Refund refund : refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED)) {
            doPushRefund(refund);
        }
    }

    private void doPushPayment(Payment payment) {
        String sapoOrderId = payment.getOrder().getSapoOrderId();
        if (sapoOrderId == null) {
            log.warn("Sapo ledger sync skipped: order {} for payment {} has no Sapo order id",
                    payment.getOrder().getId(), payment.getId());
            return;
        }
        try {
            SapoTransactionResponse response = sapoApiClient.createTransaction(sapoOrderId, buildPaymentRequest(payment));
            applyPaymentSuccess(payment, response.getTransaction().getId());
        } catch (RuntimeException e) {
            applyPaymentFailure(payment, e.getMessage());
        }
    }

    private void doPushRefund(Refund refund) {
        String sapoOrderId = refund.getOrder().getSapoOrderId();
        if (sapoOrderId == null) {
            log.warn("Sapo ledger sync skipped: order {} for refund {} has no Sapo order id",
                    refund.getOrder().getId(), refund.getId());
            return;
        }
        try {
            SapoTransactionResponse response = sapoApiClient.createTransaction(sapoOrderId, buildRefundRequest(refund));
            applyRefundSuccess(refund, response.getTransaction().getId());
        } catch (RuntimeException e) {
            applyRefundFailure(refund, e.getMessage());
        }
    }

    private SapoTransactionRequest buildPaymentRequest(Payment payment) {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(payment.getAmount())
                .kind("sale")
                .gateway(mapGateway(payment.getPaymentMethod()))
                .currency("VND")
                .status("success")
                .build();
        return SapoTransactionRequest.builder().transaction(transaction).build();
    }

    private SapoTransactionRequest buildRefundRequest(Refund refund) {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(refund.getAmount())
                .kind("refund")
                .gateway(mapGateway(refund.getOrder().getPaymentMethod()))
                .currency("VND")
                .status("success")
                .build();
        return SapoTransactionRequest.builder().transaction(transaction).build();
    }

    private String mapGateway(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case COD -> "Cash on Delivery";
            case BANK_TRANSFER -> "Bank Transfer";
            case VNPAY -> "VNPay";
            case MOMO -> "MoMo";
        };
    }

    private void applyPaymentSuccess(Payment payment, String sapoTransactionId) {
        payment.setSapoTransactionId(sapoTransactionId);
        payment.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        payment.setSapoSyncError(null);
        payment.setSapoSyncedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void applyPaymentFailure(Payment payment, String errorMessage) {
        payment.setSapoSyncStatus(SapoSyncStatus.FAILED);
        payment.setSapoSyncError(truncate(errorMessage));
        paymentRepository.save(payment);
        log.error("Sapo ledger sync failed for payment {}: {}", payment.getId(), errorMessage);
    }

    private void applyRefundSuccess(Refund refund, String sapoTransactionId) {
        refund.setSapoTransactionId(sapoTransactionId);
        refund.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        refund.setSapoSyncError(null);
        refund.setSapoSyncedAt(LocalDateTime.now());
        refundRepository.save(refund);
    }

    private void applyRefundFailure(Refund refund, String errorMessage) {
        refund.setSapoSyncStatus(SapoSyncStatus.FAILED);
        refund.setSapoSyncError(truncate(errorMessage));
        refundRepository.save(refund);
        log.error("Sapo ledger sync failed for refund {}: {}", refund.getId(), errorMessage);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > SAPO_SYNC_ERROR_MAX_LENGTH ? message.substring(0, SAPO_SYNC_ERROR_MAX_LENGTH) : message;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=SapoLedgerSyncServiceTest`
Expected: PASS (13 tests, 0 failures).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoLedgerSyncServiceTest.java
git commit -m "feat(ledger): add SapoLedgerSyncService with push and retry"
```

---

## Task 7: Wire VnPayController

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/controller/VnPayController.java`
- Test: `src/test/java/com/fashionvista/backend/controller/VnPayControllerTest.java`

**Interfaces:**
- Consumes: `SapoLedgerSyncService.pushPaymentTransaction(Long paymentId)` (Task 6).
- Produces: nothing new for later tasks.

- [ ] **Step 1: Write the failing test**

If `src/test/java/com/fashionvista/backend/controller/VnPayControllerTest.java` does not yet exist, check first:

Run: `ls src/test/java/com/fashionvista/backend/controller/VnPayControllerTest.java 2>/dev/null && echo EXISTS || echo MISSING`

If `MISSING`, create it with this minimal skeleton that exercises `processPaymentResult` through its public entry point (adjust the entry-point method name/signature to match whichever public method in `VnPayController` calls `processPaymentResult` — typically `handleReturnUrl`/`handleIpn`; inspect the controller's `@GetMapping`/`@PostMapping` methods to confirm):
```java
package com.fashionvista.backend.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.service.LoyaltyService;
import com.fashionvista.backend.service.OrderService;
import com.fashionvista.backend.service.VnPayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VnPayControllerTest {

    @Mock
    private VnPayService vnPayService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoyaltyService loyaltyService;

    @Mock
    private OrderService orderService;

    @Mock
    private SapoLedgerSyncService sapoLedgerSyncService;

    @InjectMocks
    private VnPayController vnPayController;

    @Test
    void processPaymentResult_SuccessfulResponse_PushesPaymentTransaction() {
        Order order = Order.builder().id(1L).orderNumber("ORD-1").build();
        Payment payment = Payment.builder().id(5L).order(order).paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(vnPayService.validateSignature(org.mockito.ArgumentMatchers.anyMap())).thenReturn(true);
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "ORD-1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "999");

        vnPayController.handleReturnUrl(params);

        verify(sapoLedgerSyncService).pushPaymentTransaction(5L);
    }
}
```

> **Implementer note:** Before writing this test, read `VnPayController.java` in full to confirm the exact public method name, parameter map key names (`vnp_TxnRef` vs `vnp_OrderNumber`, etc.), and `vnPayService.validateSignature` signature already in the file — adjust the mock stubbing and method call above to match exactly. Do not guess; the file is short (164 lines) and already implements this logic end-to-end for the non-Sapo path.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=VnPayControllerTest`
Expected: COMPILE FAILURE or FAIL — `sapoLedgerSyncService` field not found on `VnPayController`, or the interaction is never invoked.

- [ ] **Step 3: Wire the call site in `VnPayController.java`**

Add the field (alongside the existing `@RequiredArgsConstructor`-injected fields, e.g. right after `private final OrderService orderService;`):
```java
    private final SapoLedgerSyncService sapoLedgerSyncService;
```

Add the import:
```java
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
```

In `processPaymentResult`, immediately after the existing:
```java
        orderRepository.save(order);
        paymentRepository.save(payment);
```
insert:
```java
        if (success) {
            sapoLedgerSyncService.pushPaymentTransaction(payment.getId());
        }
```
This goes *before* the existing `if (success) { orderService.decreaseStockForOrder(order); loyaltyService.awardPointsForOrder(order); }` block — both `if (success)` blocks stay separate rather than merged, so a future change to one doesn't risk silently dropping the other.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=VnPayControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/controller/VnPayController.java src/test/java/com/fashionvista/backend/controller/VnPayControllerTest.java
git commit -m "feat(ledger): push Sapo transaction on successful VNPay payment"
```

---

## Task 8: Wire AdminPaymentServiceImpl

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/service/impl/AdminPaymentServiceImpl.java`
- Test: `src/test/java/com/fashionvista/backend/service/impl/AdminPaymentServiceImplTest.java`

**Interfaces:**
- Consumes: `SapoLedgerSyncService.pushPaymentTransaction(Long paymentId)` (Task 6).
- Produces: nothing new for later tasks.

**Scope note:** hook all three real `PAID`-transition points in this file — `updatePaymentStatus` (guarded, since it accepts any target status) and both loops inside `syncCodDeliveredPayments()` (each already only reachable when the payment is transitioning to `PAID`, so no extra guard needed there).

- [ ] **Step 1: Write the failing tests**

If `src/test/java/com/fashionvista/backend/service/impl/AdminPaymentServiceImplTest.java` does not exist, create it (adjust constructor/mocks to match the class's actual dependencies — `PaymentRepository`, `OrderRepository` — confirmed from the file already read):
```java
package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SapoLedgerSyncService sapoLedgerSyncService;

    @InjectMocks
    private AdminPaymentServiceImpl adminPaymentService;

    @Test
    void updatePaymentStatus_TransitionsToPaid_PushesSapoTransaction() {
        Order order = Order.builder().id(1L).status(OrderStatus.DELIVERED).build();
        Payment payment = Payment.builder().id(7L).order(order).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        adminPaymentService.updatePaymentStatus(7L, PaymentStatus.PAID);

        verify(sapoLedgerSyncService).pushPaymentTransaction(7L);
    }

    @Test
    void updatePaymentStatus_TransitionsToFailed_DoesNotPushSapoTransaction() {
        Order order = Order.builder().id(2L).status(OrderStatus.PENDING).build();
        Payment payment = Payment.builder().id(8L).order(order).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        adminPaymentService.updatePaymentStatus(8L, PaymentStatus.FAILED);

        verify(sapoLedgerSyncService, never()).pushPaymentTransaction(any());
    }

    @Test
    void syncCodDeliveredPayments_DeliveredOrderWithPendingPayment_PushesSapoTransaction() {
        Order order = Order.builder().id(3L).status(OrderStatus.DELIVERED).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING).build();
        Payment payment = Payment.builder().id(9L).order(order).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(orderRepository.findByStatusAndPaymentMethodAndPaymentStatus(
                        OrderStatus.DELIVERED, PaymentMethod.COD, PaymentStatus.PENDING))
                .thenReturn(List.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        when(orderRepository.findByPaymentMethodAndPaymentStatus(PaymentMethod.COD, PaymentStatus.PAID))
                .thenReturn(List.of());

        adminPaymentService.syncCodDeliveredPayments();

        verify(sapoLedgerSyncService, times(1)).pushPaymentTransaction(9L);
    }
}
```

> **Implementer note:** `syncCodDeliveredPayments()`'s two loops each query orders/payments through specific repository methods already present in the file (read `AdminPaymentServiceImpl.java` in full first to get their exact names and argument order — do not guess). Adjust the `when(orderRepository...)`/`when(paymentRepository...)` stubs above to match the file's real repository calls exactly; the assertions on `sapoLedgerSyncService` interactions are what this task is actually testing.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=AdminPaymentServiceImplTest`
Expected: COMPILE FAILURE or FAIL — `sapoLedgerSyncService` not found / never invoked.

- [ ] **Step 3: Wire all three call sites in `AdminPaymentServiceImpl.java`**

Add the field:
```java
    private final SapoLedgerSyncService sapoLedgerSyncService;
```

Add the import:
```java
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
```

In `updatePaymentStatus`, immediately after the existing:
```java
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
```
insert:
```java
        if (paymentStatus == PaymentStatus.PAID) {
            sapoLedgerSyncService.pushPaymentTransaction(saved.getId());
        }
```
(before the method's `return toAdminPaymentResponse(saved);`)

In `syncCodDeliveredPayments()`, first loop — immediately after:
```java
            payment.setPaymentStatus(PaymentStatus.PAID);
            paymentRepository.save(payment);
```
insert:
```java
            sapoLedgerSyncService.pushPaymentTransaction(payment.getId());
```
(before the subsequent `order.setPaymentStatus(PaymentStatus.PAID); orderRepository.save(order); count++;` lines of that same loop iteration)

In `syncCodDeliveredPayments()`, second loop — immediately after:
```java
                payment.setPaymentStatus(PaymentStatus.PAID);
                paymentRepository.save(payment);
```
insert:
```java
                sapoLedgerSyncService.pushPaymentTransaction(payment.getId());
```
(before `count++;` of that same loop iteration)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=AdminPaymentServiceImplTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/service/impl/AdminPaymentServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/AdminPaymentServiceImplTest.java
git commit -m "feat(ledger): push Sapo transaction on all admin PAID transitions"
```

---

## Task 9: Wire AdminOrderServiceImpl.createPartialRefund

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/service/impl/AdminOrderServiceImpl.java`
- Test: `src/test/java/com/fashionvista/backend/service/impl/AdminOrderServiceImplTest.java`

**Interfaces:**
- Consumes: `SapoLedgerSyncService.pushRefundTransaction(Long refundId)` (Task 6).
- Produces: nothing new for later tasks.

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/com/fashionvista/backend/service/impl/AdminOrderServiceImplTest.java` (create the class if it does not already exist; if it exists, add this test alongside the existing ones, reusing the file's existing mock fields for `orderRepository`, `refundRepository`, `paymentRepository`, `objectMapper`, `userContextService`, and adding a new `@Mock private SapoLedgerSyncService sapoLedgerSyncService;` plus this field on the `@InjectMocks` constructor call if the class is constructed manually rather than via `@InjectMocks`):

```java
    @Test
    void createPartialRefund_SavesRefundAndOrder_PushesSapoRefundTransaction() {
        Order order = Order.builder().id(1L).paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.VNPAY).build();
        Payment payment = Payment.builder().id(2L).order(order).amount(new BigDecimal("100000"))
                .refundAmount(BigDecimal.ZERO).build();
        Refund savedRefund = Refund.builder().id(3L).order(order).amount(new BigDecimal("50000"))
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenReturn(savedRefund);

        PartialRefundRequest request = new PartialRefundRequest();
        request.setAmount(new BigDecimal("50000"));
        request.setRefundMethod(RefundMethod.ORIGINAL);
        request.setReason("Customer request");
        request.setItemIds(List.of());

        adminOrderService.createPartialRefund(1L, request);

        verify(sapoLedgerSyncService).pushRefundTransaction(3L);
    }
```

> **Implementer note:** Read `AdminOrderServiceImplTest.java` first (if it exists) to reuse its existing mock setup, `PartialRefundRequest` construction style, and `userContextService`/`objectMapper` stubbing conventions rather than duplicating them — this file's other `createPartialRefund` tests already stub the same collaborators. If the file does not exist yet, create it with `@ExtendWith(MockitoExtension.class)`, `@Mock`s for `OrderRepository`, `OrderHistoryRepository`, `OrderItemRepository`, `RefundRepository`, `PaymentRepository`, `ProductRepository`, `ProductVariantRepository`, `ObjectMapper`, `UserContextService`, `EmailService`, `LoyaltyService`, `SapoOrderSyncService`, `SapoInventorySyncService`, and the new `SapoLedgerSyncService`, with `@InjectMocks private AdminOrderServiceImpl adminOrderService;`, stubbing `objectMapper.writeValueAsString(any())` to return `"[]"` and `userContextService.getCurrentUser()` to return a non-null username string, since `createPartialRefund` calls both unconditionally before building the `Refund`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AdminOrderServiceImplTest#createPartialRefund_SavesRefundAndOrder_PushesSapoRefundTransaction`
Expected: COMPILE FAILURE or FAIL — `sapoLedgerSyncService` not found / never invoked.

- [ ] **Step 3: Wire the call site in `AdminOrderServiceImpl.java`**

Add the field (alongside the existing `sapoOrderSyncService`/`sapoInventorySyncService` fields):
```java
    private final SapoLedgerSyncService sapoLedgerSyncService;
```

Add the import:
```java
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
```

In `createPartialRefund`, immediately after the existing:
```java
        paymentRepository.save(payment);
        orderRepository.save(order);
```
insert:
```java
        sapoLedgerSyncService.pushRefundTransaction(refund.getId());
```
(before the subsequent `recordHistory(...)` call — at this point `refund`, `payment`, and `order` all reflect their final saved state)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AdminOrderServiceImplTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/service/impl/AdminOrderServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/AdminOrderServiceImplTest.java
git commit -m "feat(ledger): push Sapo refund transaction on partial refund creation"
```

---

## Task 10: Full Suite Verification

**Files:**
- None (verification only).

**Interfaces:**
- Consumes: everything built in Tasks 1-9.
- Produces: nothing.

- [ ] **Step 1: Run the full test suite**

Run: `./mvnw test`
Expected: PASS, 0 failures, 0 errors — confirms the `V13` migration validates cleanly under Flyway against the full existing migration history, and no other test (e.g. any test that constructs `VnPayController`, `AdminPaymentServiceImpl`, or `AdminOrderServiceImpl` via `@InjectMocks`/manual `new`) broke from the new constructor field.

- [ ] **Step 2: Commit (only if verification required fixes)**

If Step 1 was green with no changes, there is nothing to commit — this task is a checkpoint, not a deliverable. If fixing a break required edits, commit them:
```bash
git add -A
git commit -m "fix(ledger): resolve full-suite regression from SapoLedgerSyncService wiring"
```

---

## Self-Review

**1. Spec coverage:**
- Push `kind=sale` on `PAID` transition (both trigger sites) → Task 6 (`buildPaymentRequest`), Task 7 (`VnPayController`), Task 8 (`AdminPaymentServiceImpl`, all 3 real transition points). ✅
- Push `kind=refund` per `Refund` row from `createPartialRefund()` → Task 6 (`buildRefundRequest`), Task 9. ✅
- Async/retry pattern matching Order/Shipping → Task 6 (`@Async`, `@Scheduled(cron = "0 45 * * * ?")`, skip+log WARN, try/catch `RuntimeException`, truncate to 500). ✅
- `sapo<Domain>Id`/`SyncStatus`/`SyncError`/`SyncedAt` naming → Task 1 field names match `Order`'s convention exactly. ✅
- Direct-call pattern, no `afterCommit` wrapper → Tasks 7, 8, 9 all call `sapoLedgerSyncService.push...` directly after the local save, matching `AdminOrderServiceImpl.pushOrder`'s convention. ✅
- New `SapoApiClient.createTransaction` → Task 4. ✅
- New DTOs → Task 3. ✅
- New async executor bean → Task 5. ✅
- Migration adding 4 nullable columns to both tables → Task 1 (corrected to `V13`). ✅
- No inbound webhook handling, no `authorization`/`capture`/`void`, no push for failed/pending attempts, no auto-refund-on-cancel, no `parent_id` → none of these appear anywhere in Tasks 1-9; confirmed absent by design. ✅
- Gateway mapping for all 4 `PaymentMethod` values → Task 6's `mapGateway` + parameterized test in Task 6. ✅
- Testing section's enumerated coverage (`SapoLedgerSyncServiceTest`, `SapoApiClientTest` additions, gateway mapping test, 3 call-site tests, migration via Flyway validation) → Tasks 1, 4, 6, 7, 8, 9, 10 all present. ✅

**2. Placeholder scan:** No "TBD"/"TODO"/"add appropriate error handling"/"similar to Task N" phrases appear in any step. Every code block is complete, real Java/SQL. The two "Implementer note" callouts (Tasks 7, 8, 9) point at exact files/methods to read for exact existing signatures rather than deferring logic — they do not withhold any code this plan is responsible for writing itself (the new Sapo-side code is fully written out in every task); they only ask the implementer to confirm pre-existing repository method names this plan does not control, which is unavoidable without re-pasting those files' full contents into every task brief.

**3. Type consistency:**
- `pushPaymentTransaction(Long paymentId)` — declared in Task 6, called identically in Tasks 7, 8 (`sapoLedgerSyncService.pushPaymentTransaction(payment.getId())` / `pushPaymentTransaction(saved.getId())` / `pushPaymentTransaction(payment.getId())`). ✅
- `pushRefundTransaction(Long refundId)` — declared in Task 6, called identically in Task 9 (`pushRefundTransaction(refund.getId())`). ✅
- `SapoTransactionRequest.Transaction` fields (`amount: BigDecimal`, `kind/gateway/currency/status: String`) — declared in Task 3, used identically in Task 6's `buildPaymentRequest`/`buildRefundRequest`, and in Task 4's/Task 3's tests. ✅
- `SapoTransactionResponse.getTransaction().getId(): String` — declared in Task 3, consumed identically in Task 6 (`response.getTransaction().getId()`) and Task 4's test. ✅
- `findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus)` — identical name/signature declared in Task 2 on both repositories, called identically in Task 6's `retryFailedTransactions`. ✅
- `SapoApiClient.createTransaction(String sapoOrderId, SapoTransactionRequest request): SapoTransactionResponse` — declared in Task 4, called identically in Task 6. ✅
- Bean name `"sapoLedgerTaskExecutor"` — declared in Task 5, referenced identically in Task 6's `@Async("sapoLedgerTaskExecutor")`. ✅

No gaps or naming drift found; nothing required a fix during this review pass.

---

Plan complete and saved to `docs/superpowers/plans/2026-08-27-sapo-ledger-sync.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
