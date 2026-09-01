# Sapo Voucher Outbound Sync & Sync-Health Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push FashionVista Vouchers to Sapo as `PriceRule` + `DiscountCode` resources on create/update, deactivate them in Sapo on local delete, and extend the existing pluggable sync-health framework (from the Inventory + Order sync-health project) with a third `VOUCHER` domain — reusing the same admin remediation UI and API.

**Architecture:** A new `SapoVoucherSyncService` mirrors `SapoOrderSyncService`'s `@Async` fire-and-forget push pattern, calling two new Sapo resources via 5 new `SapoApiClient` methods (`createPriceRule`, `updatePriceRule`, `getPriceRule`, `createDiscountCode`, `updateDiscountCode`). `AdminVoucherServiceImpl` schedules pushes/deactivations via `TransactionSynchronizationManager.afterCommit()`, same as `AdminOrderServiceImpl` does for orders. A new `VoucherSyncHealthCheck` implements the existing `SapoSyncHealthCheck` interface and is auto-collected by `SyncHealthScheduler` — zero scheduler changes needed. `AdminSyncHealthController` gains a 5th constructor dependency and branches on `SyncDomain.VOUCHER` in `pushToSapo`/`pullFromSapo`. The Admin React page gains a `Voucher` domain filter option and a VOUCHER action-button case (Push + Pull + Resolve, no Link).

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring Data JPA, Spring `@Async` + `TransactionSynchronizationManager`, Maven, JUnit 5 + Mockito + AssertJ + Spring `MockRestServiceServer`; React 18 + Vite + TypeScript + Tailwind CSS on the Admin side, Axios for HTTP.

**Spec:** `D:\FashionVista\FashionVista_Backend\docs\superpowers\specs\2026-08-20-sapo-voucher-sync-design.md`

## Global Constraints

- Zero new environment variables or config keys — reuses the existing `SapoOutboundProperties` (`apiKey`/`apiSecret`/`storeDomain`) that `SapoApiClient` already uses for Inventory/Order pushes (spec's Auth & Config section).
- Sapo remote ids (`sapoPriceRuleId`, `sapoDiscountCodeId`) are typed `Long` end-to-end (entity column, DTOs, `SapoApiClient` method signatures) — a deliberate departure from the existing `String`-typed `sapoProductId`/`sapoVariantId`/`sapoOrderId` columns, per the spec's Data Model section (BIGINT).
- `VoucherSyncHealthCheck`'s `PENDING`-grace-period threshold is 10 minutes, matching the spec's Sync-Health Domain Check section. `SyncHealthScheduler`'s existing 30-minute run cadence is unchanged — `VoucherSyncHealthCheck` is auto-collected via the `SapoSyncHealthCheck` interface, no scheduler code changes required.
- Deactivate-on-delete is best-effort and never blocks the local delete: `deleteVoucher()` must capture `voucher.getSapoPriceRuleId()` **before** calling `voucherRepository.delete(voucher)`, then schedule the deactivate call after commit; a failed Sapo call is logged only.
- On partial push failure (price-rule call succeeds, discount-code call fails), the price-rule id IS persisted locally before the discount-code attempt, so a retry updates the existing remote price rule instead of creating an orphaned duplicate in Sapo.
- `pull-from-sapo` for `VOUCHER` overwrites local `value` and `expiresAt` only — never `active`, `code`, or `usageLimit` (per spec).
- `link-sapo-order` stays `ORDER`-only; a `VOUCHER` discrepancy hitting that endpoint falls through to the controller's existing generic `IllegalArgumentException` → HTTP 400 (`GlobalExceptionHandler.handleIllegalArgument()`) with no code change to that endpoint.
- Real Sapo Admin API shapes (`PriceRule` + child `DiscountCode`; `POST/PUT/GET /admin/price_rules(.json|/{id}.json)`; `POST/PUT /admin/price_rules/{id}/discount_codes(.json|/{discount_code_id}.json)`) were verified against `support.sapo.vn` during spec design, per the Backend CLAUDE.md Sapo Integration Rule — do not deviate from these paths.
- No repository-level tests exist anywhere in this Backend codebase (established convention) — Task 2's repository change is NOT given a dedicated test file, only a compile check.
- `SapoApiClient` DOES have a dedicated test file (`SapoApiClientTest.java`, `MockRestServiceServer`-based) — Task 4 adds real request/response tests there, not a bare compile check.
- No application-level frontend test files exist anywhere in this Admin codebase (established convention) — Task 10 is verified via `npx tsc --noEmit`, `npm run lint`, and manual dev-server check, not unit tests.
- Admin UI changes target the worktree at `D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health\` — the same worktree the prior sync-health Admin task used — not the main Admin repo checkout.
- One commit per repo; do not mix Backend and Admin changes in the same commit.
- Never modify `.env` files. Never push without explicit confirmation.

---

### Task 1: Voucher entity fields + SyncDomain.VOUCHER

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\entity\Voucher.java`
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\entity\SyncDomain.java`

**Interfaces:**
- Produces: `Voucher.sapoPriceRuleId` (`Long`), `Voucher.sapoDiscountCodeId` (`Long`), `Voucher.sapoSyncStatus` (`SapoSyncStatus`, defaults to `PENDING`); `SyncDomain.VOUCHER` enum constant — consumed by every later task.

- [ ] **Step 1: Add the three new columns to `Voucher.java`**

In `Voucher.java`, add the import and the three new fields right after the existing `expiresAt` field (after line 101, before the `createdAt` field):

```java
import com.fashionvista.backend.entity.SapoSyncStatus;
```

(Note: `SapoSyncStatus` is in the same `com.fashionvista.backend.entity` package as `Voucher`, so no import is actually needed — just add the fields.)

```java
    /**
     * ID của Price Rule tương ứng bên Sapo (BIGINT). Null nếu chưa từng đẩy lên Sapo.
     */
    @Column(name = "sapo_price_rule_id")
    private Long sapoPriceRuleId;

    /**
     * ID của Discount Code tương ứng bên Sapo (BIGINT). Null nếu chưa từng đẩy lên Sapo.
     */
    @Column(name = "sapo_discount_code_id")
    private Long sapoDiscountCodeId;

    /**
     * Trạng thái đồng bộ với Sapo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_sync_status", nullable = false, length = 32)
    @Builder.Default
    private SapoSyncStatus sapoSyncStatus = SapoSyncStatus.PENDING;
```

The full field block (inserted between `expiresAt` and `createdAt`) now reads:

```java
    /**
     * Thời gian hết hiệu lực.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * ID của Price Rule tương ứng bên Sapo (BIGINT). Null nếu chưa từng đẩy lên Sapo.
     */
    @Column(name = "sapo_price_rule_id")
    private Long sapoPriceRuleId;

    /**
     * ID của Discount Code tương ứng bên Sapo (BIGINT). Null nếu chưa từng đẩy lên Sapo.
     */
    @Column(name = "sapo_discount_code_id")
    private Long sapoDiscountCodeId;

    /**
     * Trạng thái đồng bộ với Sapo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_sync_status", nullable = false, length = 32)
    @Builder.Default
    private SapoSyncStatus sapoSyncStatus = SapoSyncStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
```

- [ ] **Step 2: Add `VOUCHER` to `SyncDomain.java`**

Replace the full file content:

```java
package com.fashionvista.backend.entity;

public enum SyncDomain {
    INVENTORY,
    ORDER,
    VOUCHER
}
```

- [ ] **Step 3: Verify it compiles**

Run: `rtk cargo` is not applicable here (Java project) — run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw compile -q
```
Expected: `BUILD SUCCESS`, no errors. (`spring.jpa.hibernate.ddl-auto=update` means the new `vouchers` columns are created automatically on next app start — no migration script needed.)

- [ ] **Step 4: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/entity/Voucher.java src/main/java/com/fashionvista/backend/entity/SyncDomain.java
git commit -m "feat(voucher): add Sapo sync fields to Voucher entity and VOUCHER sync domain"
```

---

### Task 2: VoucherRepository finder methods

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\repository\VoucherRepository.java`

**Interfaces:**
- Consumes: `Voucher.active` (`boolean`), `Voucher.sapoSyncStatus` (`SapoSyncStatus`) from Task 1.
- Produces: `VoucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus): List<Voucher>`, `VoucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus): List<Voucher>` — consumed by Task 7 (`VoucherSyncHealthCheck`).

- [ ] **Step 1: Add the two finder methods**

Replace the full file content:

```java
package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long>, JpaSpecificationExecutor<Voucher> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    List<Voucher> findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus sapoSyncStatus);

    List<Voucher> findByActiveTrueAndSapoSyncStatus(SapoSyncStatus sapoSyncStatus);
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw compile -q
```
Expected: `BUILD SUCCESS`. (No dedicated repository test file — per the established repo convention in Global Constraints.)

- [ ] **Step 3: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/repository/VoucherRepository.java
git commit -m "feat(voucher): add sync-status finder methods to VoucherRepository"
```

---

### Task 3: Sapo PriceRule / DiscountCode DTOs

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoPriceRuleRequest.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoPriceRuleResponse.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoDiscountCodeRequest.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\dto\SapoDiscountCodeResponse.java`

**Interfaces:**
- Produces: `SapoPriceRuleRequest` (+ nested `PriceRule` with `title, valueType, value, targetType, usageLimit, startsOn, endsOn`, builder-based), `SapoPriceRuleResponse` (+ nested `PriceRule` with `id, title, valueType, value, targetType, usageLimit, startsOn, endsOn`, mutable/Jackson-deserializable), `SapoDiscountCodeRequest` (+ nested `DiscountCode` with `code`), `SapoDiscountCodeResponse` (+ nested `DiscountCode` with `id, code, usageCount`) — consumed by Task 4 (`SapoApiClient`), Task 6 (`SapoVoucherSyncService`), Task 7 (`VoucherSyncHealthCheck`).

These follow the exact existing DTO convention (`SapoProductPushRequest`/`SapoProductPushResponse`): `@Value @Builder @JsonInclude(NON_NULL)` for outbound request DTOs, `@Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)` for inbound response DTOs. No dedicated test file for DTOs (none exist for the existing product/order DTOs either) — they're exercised indirectly by Task 4's `SapoApiClient` tests.

- [ ] **Step 1: Create `SapoPriceRuleRequest.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoPriceRuleRequest {

    PriceRule priceRule;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PriceRule {
        String title;

        @JsonProperty("value_type")
        String valueType;

        String value;

        @JsonProperty("target_type")
        String targetType;

        @JsonProperty("usage_limit")
        Integer usageLimit;

        @JsonProperty("starts_on")
        String startsOn;

        @JsonProperty("ends_on")
        String endsOn;
    }
}
```

- [ ] **Step 2: Create `SapoPriceRuleResponse.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoPriceRuleResponse {

    private PriceRule priceRule;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceRule {
        private Long id;
        private String title;

        @JsonProperty("value_type")
        private String valueType;

        private String value;

        @JsonProperty("target_type")
        private String targetType;

        @JsonProperty("usage_limit")
        private Integer usageLimit;

        @JsonProperty("starts_on")
        private String startsOn;

        @JsonProperty("ends_on")
        private String endsOn;
    }
}
```

- [ ] **Step 3: Create `SapoDiscountCodeRequest.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoDiscountCodeRequest {

    DiscountCode discountCode;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DiscountCode {
        String code;
    }
}
```

- [ ] **Step 4: Create `SapoDiscountCodeResponse.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoDiscountCodeResponse {

    private DiscountCode discountCode;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiscountCode {
        private Long id;
        private String code;

        @JsonProperty("usage_count")
        private Integer usageCount;
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoPriceRuleRequest.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoPriceRuleResponse.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoDiscountCodeRequest.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoDiscountCodeResponse.java
git commit -m "feat(voucher): add Sapo PriceRule and DiscountCode DTOs"
```

---

### Task 4: SapoApiClient PriceRule/DiscountCode methods

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\client\SapoApiClient.java`
- Modify: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\client\SapoApiClientTest.java`

**Interfaces:**
- Consumes: `SapoPriceRuleRequest`, `SapoPriceRuleResponse`, `SapoDiscountCodeRequest`, `SapoDiscountCodeResponse` from Task 3.
- Produces: `SapoApiClient.createPriceRule(SapoPriceRuleRequest): SapoPriceRuleResponse`, `SapoApiClient.updatePriceRule(Long, SapoPriceRuleRequest): SapoPriceRuleResponse`, `SapoApiClient.getPriceRule(Long): SapoPriceRuleResponse`, `SapoApiClient.createDiscountCode(Long, SapoDiscountCodeRequest): SapoDiscountCodeResponse`, `SapoApiClient.updateDiscountCode(Long, Long, SapoDiscountCodeRequest): SapoDiscountCodeResponse` — consumed by Task 6 (`SapoVoucherSyncService`) and Task 7 (`VoucherSyncHealthCheck`).

- [ ] **Step 1: Write the failing tests in `SapoApiClientTest.java`**

Add these imports right after the existing `import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;` line (line 9):

```java
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
```

Add these two private helper methods right after the existing `sampleRequest()` method (after line 31, before the `createProduct_...` test):

```java
    private SapoPriceRuleRequest samplePriceRuleRequest() {
        SapoPriceRuleRequest.PriceRule priceRule = SapoPriceRuleRequest.PriceRule.builder()
                .title("SUMMER10")
                .valueType("percentage")
                .value("10")
                .usageLimit(100)
                .startsOn("2026-08-01T00:00:00")
                .endsOn("2026-09-01T00:00:00")
                .build();
        return SapoPriceRuleRequest.builder().priceRule(priceRule).build();
    }

    private SapoDiscountCodeRequest sampleDiscountCodeRequest() {
        SapoDiscountCodeRequest.DiscountCode discountCode = SapoDiscountCodeRequest.DiscountCode.builder()
                .code("SUMMER10")
                .build();
        return SapoDiscountCodeRequest.builder().discountCode(discountCode).build();
    }
```

Add these five test methods at the end of the class, right before the final closing `}` (after the existing `updateProduct_PutsToProductByIdAndParsesResponse` test, line 68):

```java
    @Test
    void createPriceRule_PostsToPriceRulesJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"price_rule\":{\"id\":501,\"value\":\"10\"}}",
                        MediaType.APPLICATION_JSON));

        SapoPriceRuleResponse response = client.createPriceRule(samplePriceRuleRequest());

        server.verify();
        assertEquals(501L, response.getPriceRule().getId());
        assertEquals("10", response.getPriceRule().getValue());
    }

    @Test
    void updatePriceRule_PutsToPriceRuleByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"price_rule\":{\"id\":501,\"value\":\"15\"}}",
                        MediaType.APPLICATION_JSON));

        SapoPriceRuleResponse response = client.updatePriceRule(501L, samplePriceRuleRequest());

        server.verify();
        assertEquals(501L, response.getPriceRule().getId());
        assertEquals("15", response.getPriceRule().getValue());
    }

    @Test
    void getPriceRule_GetsPriceRuleByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"price_rule\":{\"id\":501,\"value\":\"10\",\"ends_on\":\"2026-09-01T00:00:00\"}}",
                        MediaType.APPLICATION_JSON));

        SapoPriceRuleResponse response = client.getPriceRule(501L);

        server.verify();
        assertEquals(501L, response.getPriceRule().getId());
        assertEquals("2026-09-01T00:00:00", response.getPriceRule().getEndsOn());
    }

    @Test
    void createDiscountCode_PostsToDiscountCodesJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501/discount_codes.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"discount_code\":{\"id\":701,\"code\":\"SUMMER10\"}}",
                        MediaType.APPLICATION_JSON));

        SapoDiscountCodeResponse response = client.createDiscountCode(501L, sampleDiscountCodeRequest());

        server.verify();
        assertEquals(701L, response.getDiscountCode().getId());
        assertEquals("SUMMER10", response.getDiscountCode().getCode());
    }

    @Test
    void updateDiscountCode_PutsToDiscountCodeByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501/discount_codes/701.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"discount_code\":{\"id\":701,\"code\":\"SUMMER10\"}}",
                        MediaType.APPLICATION_JSON));

        SapoDiscountCodeResponse response = client.updateDiscountCode(501L, 701L, sampleDiscountCodeRequest());

        server.verify();
        assertEquals(701L, response.getDiscountCode().getId());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=SapoApiClientTest" -q
```
Expected: FAIL — compile error, `createPriceRule`/`updatePriceRule`/`getPriceRule`/`createDiscountCode`/`updateDiscountCode` are not defined on `SapoApiClient`.

- [ ] **Step 3: Implement the 5 new methods in `SapoApiClient.java`**

Add these imports right after the existing `import com.fashionvista.backend.integration.sapo.dto.SapoOrderPushResponse;` line (line 5):

```java
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
```

Add these 5 methods at the end of the class, right after the existing `getProduct` method (after line 78, before the final closing `}`):

```java
    public SapoPriceRuleResponse createPriceRule(SapoPriceRuleRequest request) {
        return restClient.post()
                .uri("/admin/price_rules.json")
                .body(request)
                .retrieve()
                .body(SapoPriceRuleResponse.class);
    }

    public SapoPriceRuleResponse updatePriceRule(Long priceRuleId, SapoPriceRuleRequest request) {
        return restClient.put()
                .uri("/admin/price_rules/{id}.json", priceRuleId)
                .body(request)
                .retrieve()
                .body(SapoPriceRuleResponse.class);
    }

    public SapoPriceRuleResponse getPriceRule(Long priceRuleId) {
        return restClient.get()
                .uri("/admin/price_rules/{id}.json", priceRuleId)
                .retrieve()
                .body(SapoPriceRuleResponse.class);
    }

    public SapoDiscountCodeResponse createDiscountCode(Long priceRuleId, SapoDiscountCodeRequest request) {
        return restClient.post()
                .uri("/admin/price_rules/{priceRuleId}/discount_codes.json", priceRuleId)
                .body(request)
                .retrieve()
                .body(SapoDiscountCodeResponse.class);
    }

    public SapoDiscountCodeResponse updateDiscountCode(Long priceRuleId, Long discountCodeId, SapoDiscountCodeRequest request) {
        return restClient.put()
                .uri("/admin/price_rules/{priceRuleId}/discount_codes/{discountCodeId}.json", priceRuleId, discountCodeId)
                .body(request)
                .retrieve()
                .body(SapoDiscountCodeResponse.class);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=SapoApiClientTest" -q
```
Expected: PASS — all 7 tests (2 existing + 5 new) green.

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java
git commit -m "feat(voucher): add PriceRule and DiscountCode methods to SapoApiClient"
```

---

### Task 5: sapoVoucherTaskExecutor async bean

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\config\AsyncConfig.java`

**Interfaces:**
- Produces: Spring bean `sapoVoucherTaskExecutor` (name string used by `@Async("sapoVoucherTaskExecutor")`) — consumed by Task 6 (`SapoVoucherSyncService`).

- [ ] **Step 1: Add the new bean**

Add this method to `AsyncConfig.java`, right after the existing `sapoOrderTaskExecutor` bean (after line 33, before the final closing `}`):

```java

    @Bean(name = "sapoVoucherTaskExecutor")
    public Executor sapoVoucherTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sapo-voucher-");
        executor.initialize();
        return executor;
    }
```

- [ ] **Step 2: Verify it compiles**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/config/AsyncConfig.java
git commit -m "feat(voucher): add sapoVoucherTaskExecutor async bean"
```

---

### Task 6: SapoVoucherSyncService (push / deactivate / pull)

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\service\SapoVoucherSyncService.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\service\SapoVoucherSyncServiceTest.java`

**Interfaces:**
- Consumes: `VoucherRepository` (Task 2), `SapoApiClient.createPriceRule/updatePriceRule/getPriceRule/createDiscountCode/updateDiscountCode` (Task 4), `sapoVoucherTaskExecutor` bean (Task 5), `Voucher.sapoPriceRuleId/sapoDiscountCodeId/sapoSyncStatus` (Task 1).
- Produces: `SapoVoucherSyncService.pushVoucher(Long voucherId): void` (`@Async`), `SapoVoucherSyncService.deactivateVoucher(Long sapoPriceRuleId): void` (`@Async`), `SapoVoucherSyncService.pullVoucher(Long voucherId): boolean` — consumed by Task 8 (`AdminVoucherServiceImpl`) and Task 9 (`AdminSyncHealthController`).

- [ ] **Step 1: Write the failing test file**

```java
package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoVoucherSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private SapoVoucherSyncService sapoVoucherSyncService;

    private static SapoPriceRuleResponse priceRuleResponse(Long id) {
        SapoPriceRuleResponse.PriceRule priceRule = new SapoPriceRuleResponse.PriceRule();
        priceRule.setId(id);
        SapoPriceRuleResponse response = new SapoPriceRuleResponse();
        response.setPriceRule(priceRule);
        return response;
    }

    private static SapoDiscountCodeResponse discountCodeResponse(Long id) {
        SapoDiscountCodeResponse.DiscountCode discountCode = new SapoDiscountCodeResponse.DiscountCode();
        discountCode.setId(id);
        SapoDiscountCodeResponse response = new SapoDiscountCodeResponse();
        response.setDiscountCode(discountCode);
        return response;
    }

    @Test
    void pushVoucher_NeverSynced_CreatesPriceRuleAndDiscountCodeAndStoresBothIds() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.createPriceRule(any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));
        when(sapoApiClient.createDiscountCode(eq(501L), any(SapoDiscountCodeRequest.class)))
                .thenReturn(discountCodeResponse(701L));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoPriceRuleId()).isEqualTo(501L);
        assertThat(voucher.getSapoDiscountCodeId()).isEqualTo(701L);
        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).updatePriceRule(any(), any());
    }

    @Test
    void pushVoucher_PriceRuleSucceedsDiscountCodeFails_MarksFailedAndKeepsPriceRuleId() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.createPriceRule(any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));
        when(sapoApiClient.createDiscountCode(eq(501L), any(SapoDiscountCodeRequest.class)))
                .thenThrow(new RuntimeException("Sapo down"));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoPriceRuleId()).isEqualTo(501L);
        assertThat(voucher.getSapoDiscountCodeId()).isNull();
        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
    }

    @Test
    void pushVoucher_AlreadySynced_CallsUpdateNotCreate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L).sapoDiscountCodeId(701L).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));
        when(sapoApiClient.updateDiscountCode(eq(501L), eq(701L), any(SapoDiscountCodeRequest.class)))
                .thenReturn(discountCodeResponse(701L));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).createPriceRule(any());
        verify(sapoApiClient, never()).createDiscountCode(any(), any());
    }

    @Test
    void pushVoucher_FreeshipType_MapsToPercentageHundredWithShippingLineTarget() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("FREESHIP1").type(VoucherType.FREESHIP)
                .sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.createPriceRule(any(SapoPriceRuleRequest.class))).thenAnswer(invocation -> {
            SapoPriceRuleRequest request = invocation.getArgument(0);
            assertThat(request.getPriceRule().getValueType()).isEqualTo("percentage");
            assertThat(request.getPriceRule().getValue()).isEqualTo("100");
            assertThat(request.getPriceRule().getTargetType()).isEqualTo("shipping_line");
            return priceRuleResponse(502L);
        });
        when(sapoApiClient.createDiscountCode(eq(502L), any(SapoDiscountCodeRequest.class)))
                .thenReturn(discountCodeResponse(702L));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
    }

    @Test
    void pushVoucher_VoucherNotFound_DoesNothing() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        sapoVoucherSyncService.pushVoucher(99L);

        verify(voucherRepository, never()).save(any());
    }

    @Test
    void deactivateVoucher_CallsUpdatePriceRuleWithEndsOnSet() {
        when(sapoApiClient.updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));

        sapoVoucherSyncService.deactivateVoucher(501L);

        verify(sapoApiClient).updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class));
    }

    @Test
    void deactivateVoucher_NullPriceRuleId_DoesNotCallSapo() {
        sapoVoucherSyncService.deactivateVoucher(null);

        verify(sapoApiClient, never()).updatePriceRule(any(), any());
    }

    @Test
    void deactivateVoucher_SapoThrows_DoesNotThrow() {
        when(sapoApiClient.updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class)))
                .thenThrow(new RuntimeException("Sapo down"));

        sapoVoucherSyncService.deactivateVoucher(501L);

        verify(sapoApiClient).updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class));
    }

    @Test
    void pullVoucher_Success_OverwritesLocalValueAndExpiresAt() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L).build();
        SapoPriceRuleResponse response = priceRuleResponse(501L);
        response.getPriceRule().setValue("20");
        response.getPriceRule().setEndsOn("2026-09-01T00:00");
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenReturn(response);
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        boolean result = sapoVoucherSyncService.pullVoucher(1L);

        assertThat(result).isTrue();
        assertThat(voucher.getValue()).isEqualByComparingTo("20");
        assertThat(voucher.getExpiresAt().toString()).isEqualTo("2026-09-01T00:00");
    }

    @Test
    void pullVoucher_NeverPushed_ReturnsFalse() {
        Voucher voucher = Voucher.builder().id(1L).code("SUMMER10").sapoPriceRuleId(null).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        boolean result = sapoVoucherSyncService.pullVoucher(1L);

        assertThat(result).isFalse();
        verify(sapoApiClient, never()).getPriceRule(any());
    }

    @Test
    void pullVoucher_SapoThrows_ReturnsFalse() {
        Voucher voucher = Voucher.builder().id(1L).code("SUMMER10").sapoPriceRuleId(501L).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenThrow(new RuntimeException("Sapo down"));

        boolean result = sapoVoucherSyncService.pullVoucher(1L);

        assertThat(result).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=SapoVoucherSyncServiceTest" -q
```
Expected: FAIL — compile error, `SapoVoucherSyncService` class does not exist.

- [ ] **Step 3: Create `SapoVoucherSyncService.java`**

```java
package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoVoucherSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoVoucherSyncService.class);
    private static final String VALUE_TYPE_PERCENTAGE = "percentage";
    private static final String VALUE_TYPE_FIXED_AMOUNT = "fixed_amount";
    private static final String TARGET_TYPE_SHIPPING_LINE = "shipping_line";

    private final SapoApiClient sapoApiClient;
    private final VoucherRepository voucherRepository;

    @Async("sapoVoucherTaskExecutor")
    @Transactional
    public void pushVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
        if (voucher == null) {
            log.warn("Sapo voucher sync: voucher id={} not found, skipping.", voucherId);
            return;
        }
        doPush(voucher);
        voucherRepository.save(voucher);
    }

    private void doPush(Voucher voucher) {
        try {
            SapoPriceRuleRequest priceRuleRequest = buildPriceRuleRequest(voucher);
            SapoPriceRuleResponse priceRuleResponse = voucher.getSapoPriceRuleId() == null
                    ? sapoApiClient.createPriceRule(priceRuleRequest)
                    : sapoApiClient.updatePriceRule(voucher.getSapoPriceRuleId(), priceRuleRequest);
            if (priceRuleResponse == null || priceRuleResponse.getPriceRule() == null
                    || priceRuleResponse.getPriceRule().getId() == null) {
                voucher.setSapoSyncStatus(SapoSyncStatus.FAILED);
                return;
            }
            voucher.setSapoPriceRuleId(priceRuleResponse.getPriceRule().getId());

            SapoDiscountCodeRequest discountCodeRequest = SapoDiscountCodeRequest.builder()
                    .discountCode(SapoDiscountCodeRequest.DiscountCode.builder().code(voucher.getCode()).build())
                    .build();
            SapoDiscountCodeResponse discountCodeResponse = voucher.getSapoDiscountCodeId() == null
                    ? sapoApiClient.createDiscountCode(voucher.getSapoPriceRuleId(), discountCodeRequest)
                    : sapoApiClient.updateDiscountCode(voucher.getSapoPriceRuleId(), voucher.getSapoDiscountCodeId(), discountCodeRequest);
            if (discountCodeResponse == null || discountCodeResponse.getDiscountCode() == null
                    || discountCodeResponse.getDiscountCode().getId() == null) {
                voucher.setSapoSyncStatus(SapoSyncStatus.FAILED);
                return;
            }
            voucher.setSapoDiscountCodeId(discountCodeResponse.getDiscountCode().getId());
            voucher.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        } catch (RuntimeException ex) {
            log.error("Sapo voucher sync failed for voucher id={}: {}", voucher.getId(), ex.getMessage(), ex);
            voucher.setSapoSyncStatus(SapoSyncStatus.FAILED);
        }
    }

    private SapoPriceRuleRequest buildPriceRuleRequest(Voucher voucher) {
        SapoPriceRuleRequest.PriceRule.PriceRuleBuilder priceRule = SapoPriceRuleRequest.PriceRule.builder()
                .title(voucher.getCode())
                .value(voucher.getValue() != null ? voucher.getValue().toPlainString() : null)
                .usageLimit(voucher.getUsageLimit())
                .startsOn(voucher.getStartsAt() != null ? voucher.getStartsAt().toString() : null)
                .endsOn(voucher.getExpiresAt() != null ? voucher.getExpiresAt().toString() : null);

        switch (voucher.getType()) {
            case PERCENT -> priceRule.valueType(VALUE_TYPE_PERCENTAGE);
            case FIXED_AMOUNT -> priceRule.valueType(VALUE_TYPE_FIXED_AMOUNT);
            case FREESHIP -> priceRule.valueType(VALUE_TYPE_PERCENTAGE).value("100").targetType(TARGET_TYPE_SHIPPING_LINE);
        }

        return SapoPriceRuleRequest.builder().priceRule(priceRule.build()).build();
    }

    @Async("sapoVoucherTaskExecutor")
    public void deactivateVoucher(Long sapoPriceRuleId) {
        if (sapoPriceRuleId == null) {
            return;
        }
        try {
            SapoPriceRuleRequest.PriceRule priceRule = SapoPriceRuleRequest.PriceRule.builder()
                    .endsOn(LocalDateTime.now().toString())
                    .build();
            sapoApiClient.updatePriceRule(sapoPriceRuleId, SapoPriceRuleRequest.builder().priceRule(priceRule).build());
        } catch (RuntimeException ex) {
            log.error("Sapo voucher deactivate failed for sapoPriceRuleId={}: {}", sapoPriceRuleId, ex.getMessage(), ex);
        }
    }

    @Transactional
    public boolean pullVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
        if (voucher == null || voucher.getSapoPriceRuleId() == null) {
            return false;
        }
        try {
            SapoPriceRuleResponse response = sapoApiClient.getPriceRule(voucher.getSapoPriceRuleId());
            if (response == null || response.getPriceRule() == null) {
                return false;
            }
            SapoPriceRuleResponse.PriceRule remote = response.getPriceRule();
            if (remote.getValue() != null) {
                voucher.setValue(new BigDecimal(remote.getValue()));
            }
            if (remote.getEndsOn() != null) {
                voucher.setExpiresAt(LocalDateTime.parse(remote.getEndsOn()));
            }
            voucherRepository.save(voucher);
            return true;
        } catch (RuntimeException ex) {
            log.error("Sapo voucher pull failed for voucher id={}: {}", voucherId, ex.getMessage(), ex);
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=SapoVoucherSyncServiceTest" -q
```
Expected: PASS — all 11 tests green.

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/service/SapoVoucherSyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoVoucherSyncServiceTest.java
git commit -m "feat(voucher): add SapoVoucherSyncService for push/deactivate/pull"
```

---

### Task 7: VoucherSyncHealthCheck

**Files:**
- Create: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\integration\sapo\synchealth\VoucherSyncHealthCheck.java`
- Test: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\integration\sapo\synchealth\VoucherSyncHealthCheckTest.java`

**Interfaces:**
- Consumes: `SapoSyncHealthCheck` interface + `DiscrepancyCandidate` record (existing), `VoucherRepository.findByActiveTrueAndSapoSyncStatusNot/And` (Task 2), `SapoApiClient.getPriceRule` (Task 4), `SyncDomain.VOUCHER` (Task 1).
- Produces: `VoucherSyncHealthCheck` (a `SapoSyncHealthCheck` Spring bean, auto-collected by the existing `SyncHealthScheduler` — no scheduler code changes) — consumed by Task 9's controller only indirectly (discrepancies it creates flow through the existing `SyncDiscrepancyService`).

- [ ] **Step 1: Write the failing test file**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoucherSyncHealthCheckTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private SapoApiClient sapoApiClient;

    @InjectMocks
    private VoucherSyncHealthCheck voucherSyncHealthCheck;

    private static SapoPriceRuleResponse responseWithValue(Long id, String value, String endsOn) {
        SapoPriceRuleResponse.PriceRule priceRule = new SapoPriceRuleResponse.PriceRule();
        priceRule.setId(id);
        priceRule.setValue(value);
        priceRule.setEndsOn(endsOn);
        SapoPriceRuleResponse response = new SapoPriceRuleResponse();
        response.setPriceRule(priceRule);
        return response;
    }

    @Test
    void checkAll_PendingWithinGracePeriod_ReturnsNoCandidates() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").sapoSyncStatus(SapoSyncStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(2)).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of());

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_PendingPastGracePeriod_ReturnsNotSyncedCandidate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").sapoSyncStatus(SapoSyncStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(15)).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of());

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(1L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.NOT_SYNCED);
    }

    @Test
    void checkAll_Failed_ReturnsSyncFailedCandidate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").sapoSyncStatus(SapoSyncStatus.FAILED)
                .createdAt(LocalDateTime.now()).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of());

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.SYNC_FAILED);
    }

    @Test
    void checkAll_SyncedValueMismatch_ReturnsValueMismatchCandidate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L)
                .expiresAt(LocalDateTime.parse("2026-09-01T00:00")).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of());
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenReturn(responseWithValue(501L, "20", "2026-09-01T00:00"));

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.VALUE_MISMATCH);
    }

    @Test
    void checkAll_SyncedValueMatches_ReturnsNoCandidates() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L)
                .expiresAt(LocalDateTime.parse("2026-09-01T00:00")).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of());
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenReturn(responseWithValue(501L, "10", "2026-09-01T00:00"));

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_SapoApiThrows_ReturnsEmptyAndDoesNotThrow() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of());
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenThrow(new RuntimeException("Sapo down"));

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void domain_ReturnsVoucher() {
        assertThat(voucherSyncHealthCheck.domain()).isEqualTo(com.fashionvista.backend.entity.SyncDomain.VOUCHER);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=VoucherSyncHealthCheckTest" -q
```
Expected: FAIL — compile error, `VoucherSyncHealthCheck` class does not exist.

- [ ] **Step 3: Create `VoucherSyncHealthCheck.java`**

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
                String localValue = voucher.getValue() != null ? voucher.getValue().toPlainString() : null;
                String localEndsOn = voucher.getExpiresAt() != null ? voucher.getExpiresAt().toString() : null;

                boolean valueMismatch = localValue != null && !localValue.equals(remoteValue);
                boolean endsOnMismatch = localEndsOn != null && !localEndsOn.equals(remoteEndsOn);

                if (valueMismatch || endsOnMismatch) {
                    candidates.add(new DiscrepancyCandidate(
                            voucher.getId(),
                            voucher.getCode(),
                            DiscrepancyType.VALUE_MISMATCH,
                            "local value=" + localValue + " vs sapo value=" + remoteValue
                                    + ", local expiresAt=" + localEndsOn + " vs sapo ends_on=" + remoteEndsOn));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo voucher sync-health check failed for voucher id={}: {}", voucher.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=VoucherSyncHealthCheckTest" -q
```
Expected: PASS — all 7 tests green.

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/integration/sapo/synchealth/VoucherSyncHealthCheck.java src/test/java/com/fashionvista/backend/integration/sapo/synchealth/VoucherSyncHealthCheckTest.java
git commit -m "feat(voucher): add VoucherSyncHealthCheck for sync-health scanning"
```

---

### Task 8: Wire push/deactivate into AdminVoucherServiceImpl

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\service\impl\AdminVoucherServiceImpl.java`
- Create: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\service\impl\AdminVoucherServiceImplTest.java`

**Interfaces:**
- Consumes: `SapoVoucherSyncService.pushVoucher(Long)`, `SapoVoucherSyncService.deactivateVoucher(Long)` (Task 6).
- Produces: `AdminVoucherServiceImpl` now schedules Sapo push after every create/update and Sapo deactivate after every delete — no new public methods (implements the existing `AdminVoucherService` interface unchanged).

This file has no existing test — `AdminVoucherServiceImplTest.java` is created from scratch in this task, covering baseline CRUD plus the new Sapo-trigger behavior. Since Mockito unit tests run outside a real Spring transaction, `TransactionSynchronizationManager.isSynchronizationActive()` returns `false`, so the `else` branch of `scheduleXAfterCommit` fires synchronously — this is exactly how the existing `AdminOrderServiceImplTest` verifies `schedulePushOrderAfterCommit` (see `updateOrderStatus_TransitionsIntoConfirmed_TriggersSapoOrderPush` in `AdminOrderServiceImplTest.java:209-224`), so the same pattern is used here.

- [ ] **Step 1: Write the failing test file**

```java
package com.fashionvista.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.AdminVoucherResponse;
import com.fashionvista.backend.dto.VoucherCreateRequest;
import com.fashionvista.backend.dto.VoucherUpdateRequest;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.integration.sapo.service.SapoVoucherSyncService;
import com.fashionvista.backend.repository.VoucherRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminVoucherServiceImplTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private SapoVoucherSyncService sapoVoucherSyncService;

    @InjectMocks
    private AdminVoucherServiceImpl adminVoucherService;

    private Voucher voucher;

    @BeforeEach
    void setUp() {
        voucher = Voucher.builder()
                .id(1L).code("SALE10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .freeShipping(false).usedCount(0).active(true).build();
    }

    @Test
    void getAllVouchers_ReturnsPageOfVouchers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(voucherRepository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(voucher)));

        Page<AdminVoucherResponse> result = adminVoucherService.getAllVouchers(null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("SALE10", result.getContent().get(0).getCode());
    }

    @Test
    void getVoucherById_Exists_ReturnsResponse() {
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        AdminVoucherResponse result = adminVoucherService.getVoucherById(1L);

        assertEquals("SALE10", result.getCode());
    }

    @Test
    void getVoucherById_NotFound_ThrowsEntityNotFoundException() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminVoucherService.getVoucherById(99L));
    }

    @Test
    void createVoucher_ValidRequest_SavesAndTriggersSapoPush() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "SALE20", VoucherType.PERCENT, BigDecimal.valueOf(20), false, null, null, true, null, null);
        when(voucherRepository.findByCodeIgnoreCase("SALE20")).thenReturn(Optional.empty());
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> {
            Voucher saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        AdminVoucherResponse result = adminVoucherService.createVoucher(request);

        assertNotNull(result);
        assertEquals("SALE20", result.getCode());
        verify(sapoVoucherSyncService).pushVoucher(2L);
    }

    @Test
    void createVoucher_DuplicateCode_ThrowsIllegalArgumentExceptionAndDoesNotPush() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "SALE10", VoucherType.PERCENT, BigDecimal.TEN, false, null, null, true, null, null);
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(voucher));

        assertThrows(IllegalArgumentException.class, () -> adminVoucherService.createVoucher(request));
        verify(sapoVoucherSyncService, never()).pushVoucher(any());
    }

    @Test
    void updateVoucher_ValidRequest_SavesAndTriggersSapoPush() {
        VoucherUpdateRequest request = new VoucherUpdateRequest(
                null, null, BigDecimal.valueOf(15), null, null, null, null, null, null);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminVoucherResponse result = adminVoucherService.updateVoucher(1L, request);

        assertEquals(BigDecimal.valueOf(15), result.getValue());
        verify(sapoVoucherSyncService).pushVoucher(1L);
    }

    @Test
    void deleteVoucher_WithSapoPriceRuleId_DeletesAndTriggersDeactivate() {
        voucher.setSapoPriceRuleId(501L);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        adminVoucherService.deleteVoucher(1L);

        verify(voucherRepository).delete(voucher);
        verify(sapoVoucherSyncService).deactivateVoucher(501L);
    }

    @Test
    void deleteVoucher_WithoutSapoPriceRuleId_DeletesAndDoesNotTriggerDeactivate() {
        voucher.setSapoPriceRuleId(null);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        adminVoucherService.deleteVoucher(1L);

        verify(voucherRepository).delete(voucher);
        verify(sapoVoucherSyncService, never()).deactivateVoucher(any());
    }

    @Test
    void deleteVoucher_NotFound_ThrowsEntityNotFoundException() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminVoucherService.deleteVoucher(99L));
        verify(sapoVoucherSyncService, never()).deactivateVoucher(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=AdminVoucherServiceImplTest" -q
```
Expected: FAIL — compile error (`SapoVoucherSyncService` not injectable into `AdminVoucherServiceImpl`, `pushVoucher`/`deactivateVoucher` never called) and behavioral failures on the two Sapo-trigger tests.

- [ ] **Step 3: Wire `SapoVoucherSyncService` into `AdminVoucherServiceImpl.java`**

Add these imports after the existing `import com.fashionvista.backend.entity.VoucherType;` line (line 7):

```java
import com.fashionvista.backend.integration.sapo.service.SapoVoucherSyncService;
```

Add after `import java.math.BigDecimal;` (line 11):

```java
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

Replace the field declaration (line 23):

```java
    private final VoucherRepository voucherRepository;
```

with:

```java
    private final VoucherRepository voucherRepository;
    private final SapoVoucherSyncService sapoVoucherSyncService;
```

Replace the end of `createVoucher` (lines 83-84):

```java
        voucher = voucherRepository.save(voucher);
        return toAdminVoucherResponse(voucher);
    }
```

with:

```java
        voucher = voucherRepository.save(voucher);
        schedulePushVoucherAfterCommit(voucher.getId());
        return toAdminVoucherResponse(voucher);
    }
```

Replace the end of `updateVoucher` (lines 139-141):

```java
        voucher = voucherRepository.save(voucher);
        return toAdminVoucherResponse(voucher);
    }
```

with:

```java
        voucher = voucherRepository.save(voucher);
        schedulePushVoucherAfterCommit(voucher.getId());
        return toAdminVoucherResponse(voucher);
    }
```

Replace the full `deleteVoucher` method (lines 143-149):

```java
    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy voucher với ID: " + id));
        voucherRepository.delete(voucher);
    }
```

with:

```java
    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy voucher với ID: " + id));
        Long sapoPriceRuleId = voucher.getSapoPriceRuleId();
        voucherRepository.delete(voucher);
        if (sapoPriceRuleId != null) {
            scheduleDeactivateVoucherAfterCommit(sapoPriceRuleId);
        }
    }
```

Add these two helper methods right before the final `toAdminVoucherResponse` private method (before line 151):

```java
    /**
     * pushVoucher() là @Async + @Transactional: gọi trực tiếp bên trong một transaction đang mở sẽ khiến
     * task async đọc dữ liệu chưa commit. Đăng ký chạy sau khi transaction hiện tại commit để tránh race condition.
     */
    private void schedulePushVoucherAfterCommit(Long voucherId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sapoVoucherSyncService.pushVoucher(voucherId);
                }
            });
        } else {
            sapoVoucherSyncService.pushVoucher(voucherId);
        }
    }

    private void scheduleDeactivateVoucherAfterCommit(Long sapoPriceRuleId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sapoVoucherSyncService.deactivateVoucher(sapoPriceRuleId);
                }
            });
        } else {
            sapoVoucherSyncService.deactivateVoucher(sapoPriceRuleId);
        }
    }

```

- [ ] **Step 4: Run test to verify it passes**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=AdminVoucherServiceImplTest" -q
```
Expected: PASS — all 9 tests green.

- [ ] **Step 5: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/service/impl/AdminVoucherServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/AdminVoucherServiceImplTest.java
git commit -m "feat(voucher): push to Sapo on create/update, deactivate on delete"
```

---

### Task 9: AdminSyncHealthController VOUCHER support

**Files:**
- Modify: `D:\FashionVista\FashionVista_Backend\src\main\java\com\fashionvista\backend\controller\AdminSyncHealthController.java`
- Modify: `D:\FashionVista\FashionVista_Backend\src\test\java\com\fashionvista\backend\controller\AdminSyncHealthControllerTest.java`

**Interfaces:**
- Consumes: `SapoVoucherSyncService.pushVoucher(Long)`, `SapoVoucherSyncService.pullVoucher(Long): boolean` (Task 6).
- Produces: `POST /api/admin/sapo/sync-health/discrepancies/{id}/push-to-sapo` and `.../pull-from-sapo` now handle `SyncDomain.VOUCHER`; `link-sapo-order` unchanged (still `ORDER`-only, `VOUCHER` falls through to the existing generic 400).

- [ ] **Step 1: Write the failing tests in `AdminSyncHealthControllerTest.java`**

Add this import after the existing `import com.fashionvista.backend.integration.sapo.service.SapoOrderSyncService;` line (line 16):

```java
import com.fashionvista.backend.integration.sapo.service.SapoVoucherSyncService;
```

Replace the field declarations and `setUp()` method (lines 25-39):

```java
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
```

with:

```java
    private SyncDiscrepancyService syncDiscrepancyService;
    private SyncHealthScheduler syncHealthScheduler;
    private SapoInventorySyncService sapoInventorySyncService;
    private SapoOrderSyncService sapoOrderSyncService;
    private SapoVoucherSyncService sapoVoucherSyncService;
    private AdminSyncHealthController controller;

    @BeforeEach
    void setUp() {
        syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        syncHealthScheduler = mock(SyncHealthScheduler.class);
        sapoInventorySyncService = mock(SapoInventorySyncService.class);
        sapoOrderSyncService = mock(SapoOrderSyncService.class);
        sapoVoucherSyncService = mock(SapoVoucherSyncService.class);
        controller = new AdminSyncHealthController(
                syncDiscrepancyService, syncHealthScheduler, sapoInventorySyncService, sapoOrderSyncService,
                sapoVoucherSyncService);
    }
```

Add these four test methods at the end of the class, right before the final closing `}` (after the existing `runNow_DelegatesToScheduler` test, line 119):

```java

    @Test
    void pushToSapo_VoucherDomain_AlwaysResolvesAfterFireAndForgetPush() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(3L).domain(SyncDomain.VOUCHER).entityId(20L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(3L)).thenReturn(discrepancy);

        controller.pushToSapo(3L);

        verify(sapoVoucherSyncService).pushVoucher(20L);
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void pullFromSapo_VoucherDomainSuccess_ResolvesDiscrepancy() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(3L).domain(SyncDomain.VOUCHER).entityId(20L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(3L)).thenReturn(discrepancy);
        when(sapoVoucherSyncService.pullVoucher(20L)).thenReturn(true);

        controller.pullFromSapo(3L);

        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void pullFromSapo_VoucherDomainFailure_DoesNotResolveDiscrepancy() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(3L).domain(SyncDomain.VOUCHER).entityId(20L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(3L)).thenReturn(discrepancy);
        when(sapoVoucherSyncService.pullVoucher(20L)).thenReturn(false);

        controller.pullFromSapo(3L);

        verify(syncDiscrepancyService, never()).resolve(any(SyncDiscrepancy.class));
    }

    @Test
    void linkSapoOrder_VoucherDomain_ThrowsIllegalArgumentException() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(3L).domain(SyncDomain.VOUCHER).entityId(20L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(3L)).thenReturn(discrepancy);
        LinkSapoOrderRequest request = new LinkSapoOrderRequest();
        request.setSapoOrderId("sapo-order-9");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.linkSapoOrder(3L, request));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=AdminSyncHealthControllerTest" -q
```
Expected: FAIL — compile error, `AdminSyncHealthController`'s constructor does not accept a 5th `SapoVoucherSyncService` argument yet.

- [ ] **Step 3: Wire `SapoVoucherSyncService` into `AdminSyncHealthController.java`**

Add this import after the existing `import com.fashionvista.backend.integration.sapo.service.SapoOrderSyncService;` line (line 8):

```java
import com.fashionvista.backend.integration.sapo.service.SapoVoucherSyncService;
```

Replace the field declaration (line 37):

```java
    private final SapoOrderSyncService sapoOrderSyncService;
```

with:

```java
    private final SapoOrderSyncService sapoOrderSyncService;
    private final SapoVoucherSyncService sapoVoucherSyncService;
```

(`@RequiredArgsConstructor` regenerates the constructor with the new field as the 5th parameter — matching the test's `new AdminSyncHealthController(..., sapoVoucherSyncService)` call.)

Replace the `pushToSapo` method body (lines 51-66):

```java
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
```

with:

```java
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
        } else if (discrepancy.getDomain() == SyncDomain.VOUCHER) {
            sapoVoucherSyncService.pushVoucher(discrepancy.getEntityId());
            syncDiscrepancyService.resolve(discrepancy);
        } else {
            throw new IllegalArgumentException("Domain không hỗ trợ push-to-sapo.");
        }
        return ResponseEntity.ok().build();
    }
```

Replace the `pullFromSapo` method body (lines 68-82):

```java
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
```

with:

```java
    @PostMapping("/discrepancies/{id}/pull-from-sapo")
    @Transactional
    public ResponseEntity<Void> pullFromSapo(@PathVariable Long id) {
        SyncDiscrepancy discrepancy = syncDiscrepancyService.findByIdOrThrow(id);

        boolean success;
        if (discrepancy.getDomain() == SyncDomain.INVENTORY) {
            success = sapoInventorySyncService.pullStock(discrepancy.getEntityId());
        } else if (discrepancy.getDomain() == SyncDomain.VOUCHER) {
            success = sapoVoucherSyncService.pullVoucher(discrepancy.getEntityId());
        } else {
            throw new IllegalArgumentException("Domain không hỗ trợ pull-from-sapo.");
        }
        if (success) {
            syncDiscrepancyService.resolve(discrepancy);
        }
        return ResponseEntity.ok().build();
    }
```

(`linkSapoOrder` needs no code change — it already throws for any `discrepancy.getDomain() != SyncDomain.ORDER`, which now correctly includes `VOUCHER`. The existing `pullFromSapo_OrderDomain_ThrowsIllegalArgumentException` test and the existing `linkSapoOrder_InventoryDomain_ThrowsIllegalArgumentException` test still pass unchanged since they only assert `IllegalArgumentException`, not message text.)

- [ ] **Step 4: Run tests to verify they pass**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test "-Dtest=AdminSyncHealthControllerTest" -q
```
Expected: PASS — all 11 tests green.

- [ ] **Step 5: Run the full backend test suite**

Run:
```powershell
cd D:\FashionVista\FashionVista_Backend
./mvnw test -q
```
Expected: `BUILD SUCCESS`, all tests pass (confirms no other caller of `new AdminSyncHealthController(...)` broke from the constructor signature change).

- [ ] **Step 6: Commit**

```powershell
cd D:\FashionVista\FashionVista_Backend
git add src/main/java/com/fashionvista/backend/controller/AdminSyncHealthController.java src/test/java/com/fashionvista/backend/controller/AdminSyncHealthControllerTest.java
git commit -m "feat(voucher): support VOUCHER domain in AdminSyncHealthController"
```

---

### Task 10: Admin UI — Voucher domain filter and actions

**Files:**
- Modify: `D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health\src\types\syncHealth.ts`
- Modify: `D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health\src\pages\admin\AdminSyncHealth.tsx`

**Interfaces:**
- Consumes: `GET /api/admin/sapo/sync-health/discrepancies?domain=VOUCHER`, `POST .../push-to-sapo`, `POST .../pull-from-sapo`, `POST .../resolve` (all already implemented by the existing `adminSyncHealthService`; Task 9 makes `VOUCHER` a valid value for `domain` server-side).
- Produces: no new exports — this task only extends the existing `SyncDomain` union type and the existing `AdminSyncHealth` page component's rendering.

No dedicated test file — per the established Admin-repo convention (Global Constraints), this task is verified via `npx tsc --noEmit`, `npm run lint`, and a manual dev-server check.

- [ ] **Step 1: Add `VOUCHER` to the `SyncDomain` union type**

In `syncHealth.ts`, replace line 1:

```ts
export type SyncDomain = 'INVENTORY' | 'ORDER';
```

with:

```ts
export type SyncDomain = 'INVENTORY' | 'ORDER' | 'VOUCHER';
```

- [ ] **Step 2: Add the `Voucher` domain filter option**

In `AdminSyncHealth.tsx`, replace the `DOMAIN_OPTIONS` constant (lines 7-11):

```tsx
const DOMAIN_OPTIONS: { label: string; value: SyncDomain | '' }[] = [
  { label: 'Tất cả', value: '' },
  { label: 'Kho (Inventory)', value: 'INVENTORY' },
  { label: 'Đơn hàng (Order)', value: 'ORDER' },
];
```

with:

```tsx
const DOMAIN_OPTIONS: { label: string; value: SyncDomain | '' }[] = [
  { label: 'Tất cả', value: '' },
  { label: 'Kho (Inventory)', value: 'INVENTORY' },
  { label: 'Đơn hàng (Order)', value: 'ORDER' },
  { label: 'Voucher', value: 'VOUCHER' },
];
```

- [ ] **Step 3: Add the VOUCHER action-button case (Push + Pull + Resolve, no Link)**

Replace the per-row action block (lines 193-220):

```tsx
                          <button
                            type="button"
                            onClick={() => handlePush(item.id)}
                            disabled={busyId === item.id}
                            className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)] disabled:opacity-50"
                          >
                            Đẩy lên Sapo
                          </button>
                          {item.domain === 'INVENTORY' && (
                            <button
                              type="button"
                              onClick={() => handlePull(item.id)}
                              disabled={busyId === item.id}
                              className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)] disabled:opacity-50"
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
```

with:

```tsx
                          <button
                            type="button"
                            onClick={() => handlePush(item.id)}
                            disabled={busyId === item.id}
                            className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)] disabled:opacity-50"
                          >
                            Đẩy lên Sapo
                          </button>
                          {(item.domain === 'INVENTORY' || item.domain === 'VOUCHER') && (
                            <button
                              type="button"
                              onClick={() => handlePull(item.id)}
                              disabled={busyId === item.id}
                              className="rounded-lg border border-[var(--border)] px-2 py-1 text-xs hover:bg-[var(--muted)] disabled:opacity-50"
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
```

- [ ] **Step 4: Type-check**

Run:
```powershell
cd D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health
npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Lint**

Run:
```powershell
cd D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health
npm run lint
```
Expected: no errors.

- [ ] **Step 6: Manual dev-server check**

Run:
```powershell
cd D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health
npm run dev
```
Open the Admin app's Sapo sync-health page. Confirm: the domain filter dropdown now shows a "Voucher" option; selecting it and running `Chạy kiểm tra ngay` (or seeding a `VOUCHER` discrepancy via the backend) shows rows with "Đẩy lên Sapo", "Lấy từ Sapo", and "Đánh dấu xong" buttons but no "Liên kết Sapo" button. Stop the dev server (`Ctrl+C`) when done.

- [ ] **Step 7: Commit**

```powershell
cd D:\FashionVista\FashionVista_Admin\.worktrees\sapo-sync-health
git add src/types/syncHealth.ts src/pages/admin/AdminSyncHealth.tsx
git commit -m "feat(sync-health): add Voucher domain filter and actions"
```

---

## Self-Review

**1. Spec coverage:**
- Sync Flow (create → PriceRule + DiscountCode, both ids stored, `SYNCED`/`FAILED`): Task 6 (`doPush`).
- Sync Flow (update → PUT both resources if `sapoPriceRuleId != null`, else treated as create): Task 6 (`doPush`'s ternary create/update branch).
- Sync Flow (delete → deactivate via `ends_on`, best-effort, never blocks local delete): Task 6 (`deactivateVoucher`) + Task 8 (`deleteVoucher` capture-before-delete).
- Sync-Health Domain Check (`PENDING` + grace period → `NOT_SYNCED`; `FAILED` → `SYNC_FAILED`; `SYNCED` + remote compare → `VALUE_MISMATCH`): Task 7 (`VoucherSyncHealthCheck`).
- Admin API (`push-to-sapo`, `pull-from-sapo` valid for VOUCHER; `link-sapo-order` 400 for VOUCHER; `run-now` auto-includes `VoucherSyncHealthCheck`): Task 9 (controller changes) + Task 7 (auto-collection, no scheduler change needed).
- Admin UI (domain filter gains Voucher; VOUCHER row gets Push+Pull+Resolve, no Link): Task 10.
- Auth & config (zero new env vars, reuses `SapoOutboundProperties`): confirmed in Global Constraints, no task introduces new config.
- Data Model (BIGINT Sapo ids): Task 1 (`Long` fields).
- Testing section (`SapoVoucherSyncServiceTest`, `VoucherSyncHealthCheckTest`, `AdminVoucherServiceImplTest` transaction-timing, controller tests for VOUCHER domain + 400 on link): Tasks 6, 7, 8, 9 respectively — all covered.

**2. Placeholder scan:** No "TBD"/"TODO"/"add appropriate handling" phrases found. Every step contains real, complete code. No task says "similar to Task N" without repeating the code.

**3. Type consistency:** `Long` used consistently for `sapoPriceRuleId`/`sapoDiscountCodeId`/`SapoPriceRuleResponse.PriceRule.id`/`SapoDiscountCodeResponse.DiscountCode.id` across Tasks 1, 3, 4, 6, 7, 8. Method names match across tasks: `pushVoucher(Long)`, `deactivateVoucher(Long)`, `pullVoucher(Long): boolean` declared in Task 6 and called with identical names/signatures in Tasks 8 and 9. `VoucherRepository.findByActiveTrueAndSapoSyncStatusNot`/`findByActiveTrueAndSapoSyncStatus` declared in Task 2, used verbatim in Task 7. `SyncDomain.VOUCHER` declared in Task 1, used verbatim in Tasks 7, 9, 10.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-20-sapo-voucher-sync.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
