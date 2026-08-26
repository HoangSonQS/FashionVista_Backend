# Sapo Shipping Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push FashionVista's GHN shipment lifecycle (create / delivered / returned / cancelled) to Sapo as Fulfillment records on the corresponding Sapo order, following the same async fire-and-forget, scheduled-retry pattern already used for Order and Customer sync.

**Architecture:** A new `SapoShippingSyncService` (mirroring `SapoOrderSyncService`) gains three new `SapoApiClient` methods (`createFulfillment`, `completeFulfillment`, `cancelFulfillment`) targeting Sapo's real Fulfillment sub-resource (`/admin/orders/{order_id}/fulfillments...json`). `Order` gains 5 persisted fields (`carrier` + 4 Sapo fulfillment-tracking fields) via a new migration. `ShippingServiceImpl` gets three new call sites into the sync service, one per existing shipping action (`createShipping`, `handleWebhook`, `cancelShipping`) — no change to its existing GHN logic or return types.

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring Data JPA/Hibernate, `RestClient` (Spring Web), Lombok, Jackson, JUnit 5, Mockito, AssertJ, Spring's `MockRestServiceServer`.

**Spec:** `docs/superpowers/specs/2026-08-27-sapo-shipping-sync-design.md`

## Global Constraints

- Do not read or write `.env` files.
- Any part touching the Sapo API must be verified against Sapo's real official docs (support.sapo.vn), not solely `docs/sapo-api-reference.md` (FashionVista's own inbound spec, not Sapo's docs) — this plan's endpoints were verified this way during design (see spec Context section).
- Do not add new dependencies unless necessary — none are needed for this plan; every task uses libraries already on the classpath (Lombok, Jackson, Spring Web `RestClient`, Spring Data JPA, JUnit 5, Mockito, AssertJ).
- Never mix commits across the three FashionVista projects; always work from this repo's worktree root.
- Never push to main without confirmation; never `git reset --hard` without confirmation.
- Follow commit convention `type(scope): description` (types: feat, fix, refactor, test, docs, chore).
- **Correction vs. spec:** the spec's Summary and Data Model sections name the new migration `V13__add_sapo_fulfillment_fields_to_orders.sql`. The latest versioned migration actually present in this worktree is `V11__add_compare_at_price_to_variants.sql` (confirmed via directory listing), so this plan creates `V12__add_sapo_fulfillment_fields_to_orders.sql` instead. Treat every `V12` reference below as authoritative over the spec's `V13`.
- **Correction vs. spec:** the spec's Testing section states "Migration gets the existing Flyway-validation test coverage — no new test infra." This project has **no Flyway dependency** (verified: zero matches for "flyway" across `pom.xml` and all `.properties` files). The test profile uses H2 with `spring.jpa.hibernate.ddl-auto=create-drop` — schema comes from JPA annotations, not migration files. There is no automated test for migration SQL in this codebase (see `V10`/`V11`, neither has dedicated test coverage); the new `V12` migration is applied manually to the real Postgres database, exactly like its predecessors. Task 1's tests instead cover the `Order` entity fields and the new `OrderRepository` query method (via Spring context loading), which is what's actually testable.

---

### Task 1: Migration, `Order` entity fields, `OrderRepository` query method

**Files:**
- Create: `src/main/resources/db/migration/V12__add_sapo_fulfillment_fields_to_orders.sql`
- Modify: `src/main/java/com/fashionvista/backend/entity/Order.java` (add 5 fields after `sapoSyncedAt`, currently around line 111-113)
- Modify: `src/main/java/com/fashionvista/backend/repository/OrderRepository.java` (add 1 derived query method)
- Test: `src/test/java/com/fashionvista/backend/entity/OrderTest.java` (new file, new package)
- Test: `src/test/java/com/fashionvista/backend/FashionVistaBackendApplicationTests.java` (re-run only, no edit — validates the new repository method compiles into a valid JPA query)

**Interfaces:**
- Produces: `Order.getCarrier()/setCarrier(String)`, `Order.getSapoFulfillmentId()/setSapoFulfillmentId(String)`, `Order.getSapoFulfillmentSyncStatus()/setSapoFulfillmentSyncStatus(SapoSyncStatus)`, `Order.getSapoFulfillmentSyncError()/setSapoFulfillmentSyncError(String)`, `Order.getSapoFulfillmentSyncedAt()/setSapoFulfillmentSyncedAt(LocalDateTime)`.
- Produces: `OrderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNull(SapoSyncStatus status): List<Order>` — consumed by Task 4's `retryFailedFulfillments()`.

- [ ] **Step 1: Write the failing test for the new entity fields**

Create `src/test/java/com/fashionvista/backend/entity/OrderTest.java`:

```java
package com.fashionvista.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void builder_SetsAndGetsSapoFulfillmentFields() {
        LocalDateTime now = LocalDateTime.now();

        Order order = Order.builder()
                .id(1L)
                .carrier("GHN")
                .sapoFulfillmentId("fulfillment-123")
                .sapoFulfillmentSyncStatus(SapoSyncStatus.SYNCED)
                .sapoFulfillmentSyncError(null)
                .sapoFulfillmentSyncedAt(now)
                .build();

        assertThat(order.getCarrier()).isEqualTo("GHN");
        assertThat(order.getSapoFulfillmentId()).isEqualTo("fulfillment-123");
        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoFulfillmentSyncError()).isNull();
        assertThat(order.getSapoFulfillmentSyncedAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=OrderTest`
Expected: COMPILE ERROR — `Order.builder()` has no `carrier(...)`, `sapoFulfillmentId(...)`, `sapoFulfillmentSyncStatus(...)`, `sapoFulfillmentSyncError(...)`, or `sapoFulfillmentSyncedAt(...)` methods yet.

- [ ] **Step 3: Add the 5 fields to `Order.java`**

Open `src/main/java/com/fashionvista/backend/entity/Order.java` and insert immediately after the existing `sapoSyncedAt` field (before the `items` field):

```java
    @Column(name = "carrier", length = 50)
    private String carrier;

    @Column(name = "sapo_fulfillment_id", length = 64)
    private String sapoFulfillmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_fulfillment_sync_status", columnDefinition = "varchar(20)")
    private SapoSyncStatus sapoFulfillmentSyncStatus;

    @Column(name = "sapo_fulfillment_sync_error", length = 500)
    private String sapoFulfillmentSyncError;

    @Column(name = "sapo_fulfillment_synced_at")
    private LocalDateTime sapoFulfillmentSyncedAt;
```

(`@Enumerated`, `@Column`, `EnumType`, and `LocalDateTime` are already imported in this file for the existing `sapoSyncStatus`/`sapoSyncedAt` fields — no new imports needed.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=OrderTest`
Expected: PASS (1 test)

- [ ] **Step 5: Write the failing test for the repository method**

Add to `FashionVistaBackendApplicationTests.contextLoads()` coverage implicitly — Spring Data JPA validates every repository method's derived query at context-startup time, so an invalid method name fails `contextLoads()` itself. No new test file is needed for this; proceed to Step 6 and let Step 7's `contextLoads()` run be the verification.

- [ ] **Step 6: Add the derived query method to `OrderRepository.java`**

Open `src/main/java/com/fashionvista/backend/repository/OrderRepository.java` and add (near the other `SapoSyncStatus`-based finder methods, importing `com.fashionvista.backend.entity.SapoSyncStatus` and `java.util.List` if not already imported):

```java
    List<Order> findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNull(SapoSyncStatus sapoFulfillmentSyncStatus);
```

- [ ] **Step 7: Run `contextLoads` to verify the derived query compiles into a valid JPA query**

Run: `./mvnw test -Dtest=FashionVistaBackendApplicationTests`
Expected: PASS — if the method name were invalid, Spring Data JPA would fail to start the `ApplicationContext` with a `PropertyReferenceException`.

- [ ] **Step 8: Create the migration file**

Create `src/main/resources/db/migration/V12__add_sapo_fulfillment_fields_to_orders.sql`:

```sql
-- Migration: Add Sapo fulfillment sync fields to orders
-- Date: 2026-08-27
-- Purpose: Support outbound Sapo Fulfillment sync for the Shipping domain -
--   durable carrier + Sapo fulfillment id/status/error/synced-at tracking.

-- 1. Durable carrier (previously computed transiently in ShippingServiceImpl, never persisted)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS carrier VARCHAR(50) DEFAULT NULL;

-- 2. Sapo's own Fulfillment id, set after first successful push; required for complete/cancel calls
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_id VARCHAR(64) DEFAULT NULL;

-- 3. Fulfillment-level push status (reuses existing SapoSyncStatus enum: PENDING/SYNCED/FAILED).
--    Kept separate from sapo_sync_status, which tracks the order-level push - a distinct Sapo API call.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_sync_status VARCHAR(20) DEFAULT NULL;

-- 4. Last fulfillment push error message
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_sync_error VARCHAR(500) DEFAULT NULL;

-- 5. Timestamp of last successful fulfillment sync
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_synced_at TIMESTAMP DEFAULT NULL;
```

This file is not exercised by the test suite (no Flyway in this project — see Global Constraints); it is applied manually against the real Postgres database, matching `V10`/`V11`'s precedent.

- [ ] **Step 9: Run the full test suite to confirm no regressions**

Run: `./mvnw test`
Expected: PASS, same count as baseline + 1 (`OrderTest`).

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/db/migration/V12__add_sapo_fulfillment_fields_to_orders.sql src/main/java/com/fashionvista/backend/entity/Order.java src/main/java/com/fashionvista/backend/repository/OrderRepository.java src/test/java/com/fashionvista/backend/entity/OrderTest.java
git commit -m "feat(shipping): add Sapo fulfillment fields to Order entity"
```

---

### Task 2: `SapoFulfillmentPushRequest`/`Response` DTOs + `SapoApiClient` fulfillment methods

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoFulfillmentPushRequest.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoFulfillmentPushResponse.java`
- Modify: `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java` (add 3 methods)
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java` (add 3 test cases)

**Interfaces:**
- Produces: `SapoFulfillmentPushRequest.builder().fulfillment(SapoFulfillmentPushRequest.Fulfillment).build()`, with `Fulfillment.builder().trackingNumber(String).trackingCompany(String).notifyCustomer(Boolean).build()`.
- Produces: `SapoFulfillmentPushResponse.getFulfillment(): SapoFulfillmentPushResponse.Fulfillment`, with `Fulfillment.getId(): String`.
- Produces: `SapoApiClient.createFulfillment(String sapoOrderId, SapoFulfillmentPushRequest request): SapoFulfillmentPushResponse`, `SapoApiClient.completeFulfillment(String sapoOrderId, String sapoFulfillmentId): SapoFulfillmentPushResponse`, `SapoApiClient.cancelFulfillment(String sapoOrderId, String sapoFulfillmentId): SapoFulfillmentPushResponse` — all consumed by Task 4's `SapoShippingSyncService`.
- Consumes: nothing new from earlier tasks.

- [ ] **Step 1: Write the failing DTO usage inside a new `SapoApiClientTest` case**

Open `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java` and add these imports:

```java
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
```

Add this helper method and the three test cases to the class body:

```java
    private SapoFulfillmentPushRequest sampleFulfillmentRequest() {
        SapoFulfillmentPushRequest.Fulfillment fulfillment = SapoFulfillmentPushRequest.Fulfillment.builder()
                .trackingNumber("GHN-ABC12345")
                .trackingCompany("GHN")
                .notifyCustomer(false)
                .build();
        return SapoFulfillmentPushRequest.builder().fulfillment(fulfillment).build();
    }

    @Test
    void createFulfillment_PostsToOrderFulfillmentsJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/fulfillments.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"fulfillment\":{\"id\":\"777\"}}", MediaType.APPLICATION_JSON));

        SapoFulfillmentPushResponse response = client.createFulfillment("555", sampleFulfillmentRequest());

        server.verify();
        assertEquals("777", response.getFulfillment().getId());
    }

    @Test
    void completeFulfillment_PostsToCompleteJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/fulfillments/777/complete.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"fulfillment\":{\"id\":\"777\"}}", MediaType.APPLICATION_JSON));

        SapoFulfillmentPushResponse response = client.completeFulfillment("555", "777");

        server.verify();
        assertEquals("777", response.getFulfillment().getId());
    }

    @Test
    void cancelFulfillment_PostsToCancelJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/fulfillments/777/cancel.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"fulfillment\":{\"id\":\"777\"}}", MediaType.APPLICATION_JSON));

        SapoFulfillmentPushResponse response = client.cancelFulfillment("555", "777");

        server.verify();
        assertEquals("777", response.getFulfillment().getId());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoApiClientTest`
Expected: COMPILE ERROR — `SapoFulfillmentPushRequest`/`Response` classes and `SapoApiClient.createFulfillment/completeFulfillment/cancelFulfillment` methods don't exist yet.

- [ ] **Step 3: Create `SapoFulfillmentPushRequest.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoFulfillmentPushRequest {

    Fulfillment fulfillment;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fulfillment {
        @JsonProperty("tracking_number")
        String trackingNumber;

        @JsonProperty("tracking_company")
        String trackingCompany;

        @JsonProperty("notify_customer")
        Boolean notifyCustomer;
    }
}
```

- [ ] **Step 4: Create `SapoFulfillmentPushResponse.java`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoFulfillmentPushResponse {

    private Fulfillment fulfillment;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fulfillment {
        private String id;
    }
}
```

- [ ] **Step 5: Add the 3 methods to `SapoApiClient.java`**

Open `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java`. Add these imports:

```java
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
```

Add these three methods to the class body (following the same `restClient.post()...` shape as the existing `createOrder`):

```java
    public SapoFulfillmentPushResponse createFulfillment(String sapoOrderId, SapoFulfillmentPushRequest request) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/fulfillments.json", sapoOrderId)
                .body(request)
                .retrieve()
                .body(SapoFulfillmentPushResponse.class);
    }

    public SapoFulfillmentPushResponse completeFulfillment(String sapoOrderId, String sapoFulfillmentId) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/fulfillments/{fulfillmentId}/complete.json", sapoOrderId, sapoFulfillmentId)
                .retrieve()
                .body(SapoFulfillmentPushResponse.class);
    }

    public SapoFulfillmentPushResponse cancelFulfillment(String sapoOrderId, String sapoFulfillmentId) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/fulfillments/{fulfillmentId}/cancel.json", sapoOrderId, sapoFulfillmentId)
                .retrieve()
                .body(SapoFulfillmentPushResponse.class);
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoApiClientTest`
Expected: PASS (5 tests: 2 existing + 3 new)

- [ ] **Step 7: Run the full test suite to confirm no regressions**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoFulfillmentPushRequest.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoFulfillmentPushResponse.java src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java
git commit -m "feat(shipping): add Sapo fulfillment DTOs and SapoApiClient methods"
```

---

### Task 3: `sapoShippingTaskExecutor` async bean

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/config/AsyncConfig.java` (add 1 bean)
- Test: `src/test/java/com/fashionvista/backend/config/AsyncConfigTest.java` (new file, new package)

**Interfaces:**
- Produces: Spring bean named `"sapoShippingTaskExecutor"` (type `ThreadPoolTaskExecutor`) — consumed by Task 4's `@Async("sapoShippingTaskExecutor")` annotations.
- Consumes: nothing new from earlier tasks.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/fashionvista/backend/config/AsyncConfigTest.java`:

```java
package com.fashionvista.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void sapoShippingTaskExecutor_IsConfiguredWithExpectedPoolSettings() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AsyncConfig.class)) {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) context.getBean("sapoShippingTaskExecutor");

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(5);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("sapo-shipping-");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AsyncConfigTest`
Expected: FAIL — `NoSuchBeanDefinitionException: No bean named 'sapoShippingTaskExecutor' available`.

- [ ] **Step 3: Add the bean to `AsyncConfig.java`**

Open `src/main/java/com/fashionvista/backend/config/AsyncConfig.java` and add, following the exact shape of the existing `sapoOrderTaskExecutor` bean:

```java
    @Bean(name = "sapoShippingTaskExecutor")
    public Executor sapoShippingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sapo-shipping-");
        executor.initialize();
        return executor;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AsyncConfigTest`
Expected: PASS

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/config/AsyncConfig.java src/test/java/com/fashionvista/backend/config/AsyncConfigTest.java
git commit -m "feat(shipping): add sapoShippingTaskExecutor async bean"
```

---

### Task 4: `SapoShippingSyncService`

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/service/SapoShippingSyncService.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/service/SapoShippingSyncServiceTest.java` (new file)

**Interfaces:**
- Consumes: `SapoApiClient.createFulfillment/completeFulfillment/cancelFulfillment` (Task 2), `OrderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNull` (Task 1), `"sapoShippingTaskExecutor"` bean (Task 3), `Order` fields (Task 1).
- Produces: `SapoShippingSyncService.pushFulfillment(Long orderId): void`, `.completeFulfillment(Long orderId): void`, `.cancelFulfillment(Long orderId): void`, `.retryFailedFulfillments(): void` — all consumed by Task 5's `ShippingServiceImpl`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/fashionvista/backend/integration/sapo/service/SapoShippingSyncServiceTest.java`:

```java
package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoShippingSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SapoShippingSyncService sapoShippingSyncService;

    @Test
    void pushFulfillment_SuccessfulCreate_SetsFulfillmentIdAndSynced() {
        Order order = Order.builder()
                .id(1L)
                .sapoOrderId("sapo-order-1")
                .trackingNumber("GHN-AAAA1111")
                .carrier("GHN")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        SapoFulfillmentPushResponse.Fulfillment fulfillment = new SapoFulfillmentPushResponse.Fulfillment();
        fulfillment.setId("999");
        SapoFulfillmentPushResponse response = new SapoFulfillmentPushResponse();
        response.setFulfillment(fulfillment);
        when(sapoApiClient.createFulfillment(eq("sapo-order-1"), any(SapoFulfillmentPushRequest.class)))
                .thenReturn(response);
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.pushFulfillment(1L);

        assertThat(order.getSapoFulfillmentId()).isEqualTo("999");
        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoFulfillmentSyncError()).isNull();
        assertThat(order.getSapoFulfillmentSyncedAt()).isNotNull();
    }

    @Test
    void pushFulfillment_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = Order.builder()
                .id(2L)
                .sapoOrderId("sapo-order-2")
                .trackingNumber("GHN-BBBB2222")
                .carrier("GHN")
                .build();
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(sapoApiClient.createFulfillment(eq("sapo-order-2"), any(SapoFulfillmentPushRequest.class)))
                .thenThrow(new RuntimeException("Sapo timeout"));
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.pushFulfillment(2L);

        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(order.getSapoFulfillmentSyncError()).isEqualTo("Sapo timeout");
    }

    @Test
    void pushFulfillment_OrderNotYetSyncedToSapo_SkipsWithoutClientCall() {
        Order order = Order.builder().id(3L).sapoOrderId(null).trackingNumber("GHN-CCCC3333").build();
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.pushFulfillment(3L);

        verify(sapoApiClient, never()).createFulfillment(anyString(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void completeFulfillment_MissingSapoFulfillmentId_SkipsWithoutClientCall() {
        Order order = Order.builder().id(4L).sapoOrderId("sapo-order-4").sapoFulfillmentId(null).build();
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.completeFulfillment(4L);

        verify(sapoApiClient, never()).completeFulfillment(anyString(), anyString());
    }

    @Test
    void completeFulfillment_HasFulfillmentId_CallsClient() {
        Order order = Order.builder().id(5L).sapoOrderId("sapo-order-5").sapoFulfillmentId("fid-5").build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.completeFulfillment(5L);

        verify(sapoApiClient, times(1)).completeFulfillment("sapo-order-5", "fid-5");
    }

    @Test
    void cancelFulfillment_MissingSapoFulfillmentId_SkipsWithoutClientCall() {
        Order order = Order.builder().id(6L).sapoOrderId("sapo-order-6").sapoFulfillmentId(null).build();
        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.cancelFulfillment(6L);

        verify(sapoApiClient, never()).cancelFulfillment(anyString(), anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelFulfillment_HasFulfillmentId_CallsClientAndClearsFulfillmentFields() {
        Order order = Order.builder()
                .id(7L)
                .sapoOrderId("sapo-order-7")
                .sapoFulfillmentId("fid-7")
                .sapoFulfillmentSyncStatus(SapoSyncStatus.SYNCED)
                .build();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.cancelFulfillment(7L);

        verify(sapoApiClient, times(1)).cancelFulfillment("sapo-order-7", "fid-7");
        assertThat(order.getSapoFulfillmentId()).isNull();
        assertThat(order.getSapoFulfillmentSyncStatus()).isNull();
    }

    @Test
    void retryFailedFulfillments_OnlyRetriesFailedRowsWithTrackingNumber() {
        Order order1 = Order.builder()
                .id(8L)
                .sapoOrderId("sapo-order-8")
                .trackingNumber("GHN-DDDD4444")
                .carrier("GHN")
                .sapoFulfillmentSyncStatus(SapoSyncStatus.FAILED)
                .build();
        when(orderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of(order1));
        SapoFulfillmentPushResponse.Fulfillment fulfillment = new SapoFulfillmentPushResponse.Fulfillment();
        fulfillment.setId("1000");
        SapoFulfillmentPushResponse response = new SapoFulfillmentPushResponse();
        response.setFulfillment(fulfillment);
        when(sapoApiClient.createFulfillment(eq("sapo-order-8"), any(SapoFulfillmentPushRequest.class)))
                .thenReturn(response);
        when(orderRepository.save(order1)).thenReturn(order1);

        sapoShippingSyncService.retryFailedFulfillments();

        verify(sapoApiClient, times(1)).createFulfillment(eq("sapo-order-8"), any(SapoFulfillmentPushRequest.class));
        assertThat(order1.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoShippingSyncServiceTest`
Expected: COMPILE ERROR — `SapoShippingSyncService` class doesn't exist yet.

- [ ] **Step 3: Create `SapoShippingSyncService.java`**

```java
package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
import com.fashionvista.backend.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoShippingSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoShippingSyncService.class);

    private final SapoApiClient sapoApiClient;
    private final OrderRepository orderRepository;

    @Async("sapoShippingTaskExecutor")
    @Transactional
    public void pushFulfillment(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo fulfillment push: order id={} not found, skipping.", orderId);
            return;
        }
        if (order.getSapoOrderId() == null) {
            log.warn("Sapo fulfillment push: order id={} has no sapoOrderId, skipping push.", orderId);
            return;
        }
        doPushFulfillment(order);
    }

    @Async("sapoShippingTaskExecutor")
    @Transactional
    public void completeFulfillment(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo fulfillment complete: order id={} not found, skipping.", orderId);
            return;
        }
        if (order.getSapoOrderId() == null || order.getSapoFulfillmentId() == null) {
            log.warn("Sapo fulfillment complete: order id={} has no sapoFulfillmentId, skipping.", orderId);
            return;
        }
        try {
            sapoApiClient.completeFulfillment(order.getSapoOrderId(), order.getSapoFulfillmentId());
        } catch (RuntimeException ex) {
            log.error("Sapo fulfillment complete failed for order id={}: {}", orderId, ex.getMessage(), ex);
        }
    }

    @Async("sapoShippingTaskExecutor")
    @Transactional
    public void cancelFulfillment(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo fulfillment cancel: order id={} not found, skipping.", orderId);
            return;
        }
        if (order.getSapoOrderId() == null || order.getSapoFulfillmentId() == null) {
            log.warn("Sapo fulfillment cancel: order id={} has no sapoFulfillmentId, skipping.", orderId);
            return;
        }
        try {
            sapoApiClient.cancelFulfillment(order.getSapoOrderId(), order.getSapoFulfillmentId());
            order.setSapoFulfillmentId(null);
            order.setSapoFulfillmentSyncStatus(null);
            order.setSapoFulfillmentSyncError(null);
            order.setSapoFulfillmentSyncedAt(null);
            orderRepository.save(order);
        } catch (RuntimeException ex) {
            log.error("Sapo fulfillment cancel failed for order id={}: {}", orderId, ex.getMessage(), ex);
        }
    }

    @Scheduled(cron = "0 30 * * * ?")
    @Transactional
    public void retryFailedFulfillments() {
        List<Order> failedOrders =
                orderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNull(SapoSyncStatus.FAILED);
        for (Order order : failedOrders) {
            doPushFulfillment(order);
        }
    }

    private void doPushFulfillment(Order order) {
        SapoFulfillmentPushRequest request = buildRequest(order);
        try {
            SapoFulfillmentPushResponse response = sapoApiClient.createFulfillment(order.getSapoOrderId(), request);
            applySuccess(order, response);
        } catch (RuntimeException ex) {
            log.error("Sapo fulfillment push failed for order id={}: {}", order.getId(), ex.getMessage(), ex);
            applyFailure(order, ex.getMessage());
        }
        orderRepository.save(order);
    }

    private SapoFulfillmentPushRequest buildRequest(Order order) {
        SapoFulfillmentPushRequest.Fulfillment fulfillment = SapoFulfillmentPushRequest.Fulfillment.builder()
                .trackingNumber(order.getTrackingNumber())
                .trackingCompany(order.getCarrier())
                .notifyCustomer(false)
                .build();
        return SapoFulfillmentPushRequest.builder().fulfillment(fulfillment).build();
    }

    private void applySuccess(Order order, SapoFulfillmentPushResponse response) {
        if (response == null || response.getFulfillment() == null) {
            applyFailure(order, "Sapo trả về phản hồi rỗng.");
            return;
        }
        order.setSapoFulfillmentId(response.getFulfillment().getId());
        order.setSapoFulfillmentSyncStatus(SapoSyncStatus.SYNCED);
        order.setSapoFulfillmentSyncError(null);
        order.setSapoFulfillmentSyncedAt(LocalDateTime.now());
    }

    private void applyFailure(Order order, String errorMessage) {
        order.setSapoFulfillmentSyncStatus(SapoSyncStatus.FAILED);
        order.setSapoFulfillmentSyncError(errorMessage);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoShippingSyncServiceTest`
Expected: PASS (8 tests)

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/service/SapoShippingSyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoShippingSyncServiceTest.java
git commit -m "feat(shipping): add SapoShippingSyncService for outbound fulfillment sync"
```

---

### Task 5: Wire `ShippingServiceImpl` to `SapoShippingSyncService`

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/service/impl/ShippingServiceImpl.java`
- Test: `src/test/java/com/fashionvista/backend/service/impl/ShippingServiceImplTest.java` (new file)

**Interfaces:**
- Consumes: `SapoShippingSyncService.pushFulfillment(Long)`, `.completeFulfillment(Long)`, `.cancelFulfillment(Long)` (Task 4).
- Produces: nothing new for later tasks — this is the final integration task for this domain.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/fashionvista/backend/service/impl/ShippingServiceImplTest.java`:

```java
package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.config.GhnConfig;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.ShippingCreateRequest;
import com.fashionvista.backend.dto.ShippingWebhookPayload;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.integration.sapo.service.SapoShippingSyncService;
import com.fashionvista.backend.repository.AddressRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.service.AdminOrderService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingServiceImplTest {

    @Mock
    private GhnConfig ghnConfig;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AdminOrderService adminOrderService;

    @Mock
    private SapoShippingSyncService sapoShippingSyncService;

    @InjectMocks
    private ShippingServiceImpl shippingService;

    @Test
    void createShipping_OrderSyncedToSapo_PushesFulfillment() {
        Order order = Order.builder().id(1L).sapoOrderId("sapo-order-1").status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(1L)).thenReturn(OrderResponse.builder().build());

        ShippingCreateRequest request = ShippingCreateRequest.builder().carrier("GHN").build();
        shippingService.createShipping("ORD-1", request);

        verify(sapoShippingSyncService, times(1)).pushFulfillment(1L);
    }

    @Test
    void createShipping_OrderNotSyncedToSapo_SkipsPush() {
        Order order = Order.builder().id(2L).sapoOrderId(null).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByOrderNumber("ORD-2")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(2L)).thenReturn(OrderResponse.builder().build());

        ShippingCreateRequest request = ShippingCreateRequest.builder().carrier("GHN").build();
        shippingService.createShipping("ORD-2", request);

        verify(sapoShippingSyncService, never()).pushFulfillment(any());
    }

    @Test
    void handleWebhook_Delivered_CompletesFulfillment() {
        Order order = Order.builder().id(3L).sapoFulfillmentId("fid-3").status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByTrackingNumber("TRACK-3")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ShippingWebhookPayload payload = ShippingWebhookPayload.builder()
                .trackingNumber("TRACK-3")
                .status("delivered")
                .build();
        shippingService.handleWebhook(payload);

        verify(sapoShippingSyncService, times(1)).completeFulfillment(3L);
    }

    @Test
    void handleWebhook_Returned_CancelsFulfillment() {
        Order order = Order.builder().id(4L).sapoFulfillmentId("fid-4").status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByTrackingNumber("TRACK-4")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ShippingWebhookPayload payload = ShippingWebhookPayload.builder()
                .trackingNumber("TRACK-4")
                .status("returned")
                .build();
        shippingService.handleWebhook(payload);

        verify(sapoShippingSyncService, times(1)).cancelFulfillment(4L);
    }

    @Test
    void handleWebhook_DeliveredWithNoFulfillmentId_SkipsWithoutClientCall() {
        Order order = Order.builder().id(5L).sapoFulfillmentId(null).status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByTrackingNumber("TRACK-5")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ShippingWebhookPayload payload = ShippingWebhookPayload.builder()
                .trackingNumber("TRACK-5")
                .status("delivered")
                .build();
        shippingService.handleWebhook(payload);

        verify(sapoShippingSyncService, never()).completeFulfillment(any());
    }

    @Test
    void cancelShipping_HasFulfillmentId_CancelsFulfillment() {
        Order order = Order.builder().id(6L).sapoFulfillmentId("fid-6").status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByOrderNumber("ORD-6")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(6L)).thenReturn(OrderResponse.builder().build());

        shippingService.cancelShipping("ORD-6", "customer request");

        verify(sapoShippingSyncService, times(1)).cancelFulfillment(6L);
    }

    @Test
    void cancelShipping_NoFulfillmentId_SkipsCancelCall() {
        Order order = Order.builder().id(7L).sapoFulfillmentId(null).status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByOrderNumber("ORD-7")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(7L)).thenReturn(OrderResponse.builder().build());

        shippingService.cancelShipping("ORD-7", "customer request");

        verify(sapoShippingSyncService, never()).cancelFulfillment(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ShippingServiceImplTest`
Expected: COMPILE ERROR — `ShippingServiceImpl` has no `SapoShippingSyncService` constructor dependency yet (Mockito `@InjectMocks` needs a matching constructor param), and behavior assertions fail because the call sites don't exist.

- [ ] **Step 3: Wire `ShippingServiceImpl.java`**

Open `src/main/java/com/fashionvista/backend/service/impl/ShippingServiceImpl.java`.

Add the import:

```java
import com.fashionvista.backend.integration.sapo.service.SapoShippingSyncService;
```

Add the new field next to the existing ones (Lombok's `@RequiredArgsConstructor` will pick it up automatically):

```java
    private final SapoShippingSyncService sapoShippingSyncService;
```

Update `createShipping` to persist `carrier` and push the fulfillment after save:

```java
    @Override
    @Transactional
    public OrderResponse createShipping(String orderNumber, ShippingCreateRequest request) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        String carrier = StringUtils.hasText(request.getCarrier()) ? request.getCarrier().toUpperCase(Locale.ROOT) : "GHN";
        String trackingNumber = carrier + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        order.setCarrier(carrier);
        order.setTrackingNumber(trackingNumber);
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING) {
            order.setStatus(OrderStatus.SHIPPING);
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        if (order.getSapoOrderId() != null) {
            sapoShippingSyncService.pushFulfillment(order.getId());
        }
        return adminOrderService.getOrderById(order.getId());
    }
```

Update `cancelShipping` to cancel the fulfillment (guarded on `sapoFulfillmentId`) after save:

```java
    @Override
    @Transactional
    public OrderResponse cancelShipping(String orderNumber, String note) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        order.setTrackingNumber(null);
        if (order.getStatus() == OrderStatus.SHIPPING) {
            order.setStatus(OrderStatus.PROCESSING);
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        if (order.getSapoFulfillmentId() != null) {
            sapoShippingSyncService.cancelFulfillment(order.getId());
        }
        return adminOrderService.getOrderById(order.getId());
    }
```

Update `handleWebhook` to call `completeFulfillment`/`cancelFulfillment` (both guarded on `sapoFulfillmentId`) after the existing status switch and save:

```java
    @Override
    @Transactional
    public void handleWebhook(ShippingWebhookPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.getTrackingNumber())) {
            return;
        }
        orderRepository.findByTrackingNumber(payload.getTrackingNumber()).ifPresent(order -> {
            String status = payload.getStatus() != null ? payload.getStatus().toLowerCase(Locale.ROOT) : "";
            switch (status) {
                case "pickedup":
                case "intransit":
                    order.setStatus(OrderStatus.SHIPPING);
                    break;
                case "delivered":
                    order.setStatus(OrderStatus.DELIVERED);
                    break;
                case "return":
                case "returned":
                    order.setStatus(OrderStatus.CANCELLED);
                    break;
                default:
                    return;
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            if (order.getSapoFulfillmentId() != null) {
                if (status.equals("delivered")) {
                    sapoShippingSyncService.completeFulfillment(order.getId());
                } else if (status.equals("return") || status.equals("returned")) {
                    sapoShippingSyncService.cancelFulfillment(order.getId());
                }
            }
        });
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ShippingServiceImplTest`
Expected: PASS (7 tests)

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/service/impl/ShippingServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/ShippingServiceImplTest.java
git commit -m "feat(shipping): wire ShippingServiceImpl to SapoShippingSyncService"
```

---

## Self-Review

**Spec coverage:**
- Push fulfillment on `createShipping` when synced → Task 5, `createShipping` change. ✅
- `completeFulfillment`/`cancelFulfillment` from `handleWebhook`'s delivered/return/returned cases → Task 5, `handleWebhook` change. ✅
- `cancelFulfillment` from manual `cancelShipping`, clearing `sapoFulfillmentId` → Task 5 (`cancelShipping` call site) + Task 4 (`cancelFulfillment` clears the 4 fulfillment fields on success). ✅
- Persist `carrier` durably → Task 1 (entity field) + Task 5 (`createShipping` now calls `order.setCarrier(carrier)`). ✅
- Persist `sapoFulfillmentId` → Task 1 (entity field) + Task 4 (`applySuccess`). ✅
- `SapoShippingSyncService` mirroring `SapoOrderSyncService`'s async/retry pattern → Task 4. ✅
- `SapoApiClient` 3 new methods → Task 2. ✅
- New DTOs → Task 2. ✅
- New async executor bean → Task 3. ✅
- Migration for the 5 columns → Task 1 (corrected to `V12`). ✅
- Error handling: try/catch around each `SapoApiClient` call, `FAILED` + error message, not rethrown → Task 4 (`doPushFulfillment`, `completeFulfillment`, `cancelFulfillment`). ✅
- Scheduled retry, create-failures only, cron offset `:30` → Task 4 (`retryFailedFulfillments`, `@Scheduled(cron = "0 30 * * * ?")`). ✅
- No retry for complete/cancel failures, no local `OrderStatus` rollback on Sapo failure → Task 4/5: `completeFulfillment`/`cancelFulfillment` in `ShippingServiceImpl` are called *after* `orderRepository.save(order)` already persisted the new `OrderStatus`, so a downstream Sapo failure cannot affect it. ✅
- Testing section (`SapoShippingSyncServiceTest`, `SapoApiClientTest`, `ShippingServiceImplTest` cases) → Tasks 2, 4, 5. ✅ (Migration test-coverage claim corrected per Global Constraints.)

**Placeholder scan:** No "TBD"/"TODO"/"implement later" strings. Every step has complete, runnable code. No task says "similar to Task N" without repeating the code.

**Type consistency:** `SapoShippingSyncService.pushFulfillment(Long orderId)`, `.completeFulfillment(Long orderId)`, `.cancelFulfillment(Long orderId)` signatures match between Task 4's implementation and Task 5's `ShippingServiceImpl` call sites and mock verifications. `SapoApiClient.createFulfillment(String, SapoFulfillmentPushRequest)`, `.completeFulfillment(String, String)`, `.cancelFulfillment(String, String)` match between Task 2's implementation, Task 2's test, and Task 4's usage. `OrderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNull(SapoSyncStatus)` matches between Task 1's declaration and Task 4's usage. `Order` field names (`carrier`, `sapoFulfillmentId`, `sapoFulfillmentSyncStatus`, `sapoFulfillmentSyncError`, `sapoFulfillmentSyncedAt`) are consistent across Tasks 1, 4, and 5.

---

**Plan complete and saved to `docs/superpowers/plans/2026-08-27-sapo-shipping-sync.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
