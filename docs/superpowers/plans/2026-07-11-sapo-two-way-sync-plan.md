# Sapo Two-Way Sync (Outbound Orchestrator) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push Product/Variant/Inventory changes made in FashionVista out to Sapo's Admin REST API (create/update, HTTP Basic Auth), and receive a webhook from Sapo when inventory changes on their side, updating local stock.

**Architecture:** New package `integration/sapo/` (client, config, service, dto, webhook) kept separate from the existing inbound `controller/sapo/` module. Outbound sync is hooked synchronously into `ProductServiceImpl.createProduct`/`updateProduct` after the local DB save. A new admin controller (`controller/AdminSapoSyncController`) exposes manual retry-sync and migrate endpoints. A new webhook controller verifies HMAC-SHA256 over the raw request body and updates local stock.

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring `RestClient` (sync HTTP client, no new Maven dependency), Spring Security (separate `securityMatcher` filter chain), JUnit5 + Mockito, `MockRestServiceServer` for HTTP client tests.

## Global Constraints

- Private-app auth only: HTTP Basic Auth, API key = username, API secret = password on every outbound call to Sapo — no OAuth2/token exchange (spec's explicit non-goal).
- No async outbox/queue — outbound sync calls happen inline within the admin request (spec's explicit non-goal); a slow/down Sapo API adds latency bounded by a 5s client timeout.
- No automatic retry/backoff job — retry is a manual admin-triggered endpoint only (`POST /api/admin/sapo/products/{id}/retry-sync`).
- No automatic Sapo-side webhook registration — done manually once a production webhook URL exists (out of scope for this plan).
- Scope is Product + Variant + Inventory only — no Orders/Customers/Vouchers/Suppliers/Purchase Orders/Stock Transfers.
- Env vars: `SAPO_OUTBOUND_API_KEY`, `SAPO_OUTBOUND_API_SECRET`, `SAPO_STORE_DOMAIN`, `SAPO_WEBHOOK_SECRET` (already added by the user; this plan only wires `application.properties` defaults — never touches `.env`).
- Migration endpoint (`POST /api/admin/sapo/products/migrate`) runs synchronously, scanning sequentially — acceptable at current catalog size (spec's explicit accepted limitation).

## Deviations from spec (disclosed, reasoned)

1. **`SapoInventorySyncService` merged into `SapoProductSyncService`.** The spec's architecture diagram lists them as two files. No dedicated inventory-only mutation trigger point exists anywhere in the codebase today — `AdminProductController` has no inventory-only endpoint, and the only two mutation paths are (a) full product edit, already covered by the two `ProductServiceImpl` hooks below, and (b) checkout's `ProductVariantRepository.decreaseStockIfEnough`, which is a JPQL bulk `UPDATE` that never loads an entity and is explicitly out of scope (see #3). Building a separate, never-called `SapoInventorySyncService` would violate YAGNI. `SapoProductSyncService.pushProduct(Product)` pushes the full product including all variant stock levels every time, which satisfies "inventory push" too.
2. **Webhook payload field names are an assumption**, not verified against a real Sapo payload — Sapo's exact `inventory_levels/update` webhook JSON schema could not be found in their docs (confirmed via a targeted web search that only surfaced Shopify's unrelated schema). The assumed shape (`variant_id`, `sku`, `inventory_quantity`) is based on Sapo's own **confirmed** product/variant field-naming convention from their documented Admin REST API. This must be verified against a real payload once the webhook is registered on Sapo (already a deferred, manual, out-of-scope step per the spec).
3. **Checkout-driven stock decrements remain unsynced to Sapo** — `ProductVariantRepository.decreaseStockIfEnough` is a JPQL bulk `UPDATE` bypassing the entity/service layer entirely; no hook point exists there without restructuring checkout, which is out of this plan's scope (consistent with the spec's own "Known limitations").
4. **`sapoSyncedAt` implemented as `LocalDateTime`, not `Instant`** as literally written in the spec. Every existing timestamp field on `Product`/`ProductVariant` (`createdAt`, `updatedAt`, `visibleUpdatedAt`) uses `LocalDateTime` — there is no `Instant` precedent anywhere in the entity layer. Using `LocalDateTime` here keeps the new columns consistent with the rest of the codebase.
5. **Admin endpoints live in `controller/AdminSapoSyncController`**, not under `integration/sapo/`. The spec mandates the two endpoint behaviors (retry-sync, migrate) but its file list never names a controller for them. Placing admin-role-protected endpoints in the existing `controller/` package (next to `AdminProductController`) matches this codebase's convention of separating admin-facing controllers from Sapo-facing ones, and — critically — means they fall through to the existing unscoped `GlobalExceptionHandler` (confirmed: it already handles `IllegalArgumentException` → 400 the same way `AdminProductController` relies on), so no new exception-handling code is needed. The Sapo-scoped `SapoExceptionHandler` (`basePackages = "com.fashionvista.backend.controller.sapo"`) correctly does not apply here, and doesn't need to.
6. **`SapoApiClient`'s Basic Auth header construction is not covered by a dedicated unit test.** It is a single `Base64.getEncoder().encodeToString(...)` call over `apiKey:apiSecret` — standard-library behavior. The tests for `SapoApiClient` instead focus on the parts with real logic: correct URL routing (create vs. update) and response parsing, using a package-private constructor that accepts a pre-built `RestClient` so `MockRestServiceServer` can intercept it (see Task 5).
7. **Webhook variant lookup (Task 9) falls back to SKU when `sapoVariantId` has no match**, beyond the spec's inbound-flow text, which names only "look up `ProductVariant` by `sapoVariantId`." Added because the existing inbound module's `PUT /api/sapo/v1/inventory/{sku}` already treats SKU as a valid correlation key — during the rollout window before every variant has a `sapoVariantId` populated (before Task 10's one-time migration runs, or for variants created locally afterward but not yet synced), this fallback lets the webhook still resolve the correct variant instead of only logging "not found." The SKU lookup is skipped entirely once a `sapoVariantId` match is found, so this is strictly additive.

---

### Task 1: `SapoSyncStatus` enum + entity columns

**Files:**
- Create: `src/main/java/com/fashionvista/backend/entity/SapoSyncStatus.java`
- Modify: `src/main/java/com/fashionvista/backend/entity/Product.java`
- Modify: `src/main/java/com/fashionvista/backend/entity/ProductVariant.java`

**Interfaces:**
- Produces: `SapoSyncStatus` enum (`PENDING`, `SYNCED`, `FAILED`); `Product.getSapoProductId()/setSapoProductId(String)`, `Product.getSapoSyncStatus()/setSapoSyncStatus(SapoSyncStatus)`, `Product.getSapoSyncError()/setSapoSyncError(String)`, `Product.getSapoSyncedAt()/setSapoSyncedAt(LocalDateTime)`, `ProductVariant.getSapoVariantId()/setSapoVariantId(String)` — all consumed by Task 6's `SapoProductSyncService` and Task 9's webhook controller.

This task has no independent test of its own (plain JPA columns with Lombok-generated accessors, `ddl-auto=update` handles the schema) — its correctness is exercised by Task 6/7/9's tests. Proceed straight to implementation.

- [ ] **Step 1: Create the `SapoSyncStatus` enum**

```java
package com.fashionvista.backend.entity;

public enum SapoSyncStatus {
    PENDING,
    SYNCED,
    FAILED
}
```

- [ ] **Step 2: Add the four Sapo sync columns to `Product.java`**

In `src/main/java/com/fashionvista/backend/entity/Product.java`, insert after the `tags` field (currently ending at line 102, right before the `// Relationships` comment on line 104):

```java
    @Column(name = "sapo_product_id")
    private String sapoProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sapo_sync_status", nullable = false)
    @Builder.Default
    private SapoSyncStatus sapoSyncStatus = SapoSyncStatus.PENDING;

    @Column(name = "sapo_sync_error", columnDefinition = "TEXT")
    private String sapoSyncError;

    @Column(name = "sapo_synced_at")
    private LocalDateTime sapoSyncedAt;

```

`EnumType`/`Enumerated` are already imported (used by `status`). `LocalDateTime` is already imported.

- [ ] **Step 3: Add the `sapoVariantId` column to `ProductVariant.java`**

In `src/main/java/com/fashionvista/backend/entity/ProductVariant.java`, insert after the `isActive` field (currently lines 57-59, right before the `@Version` block):

```java
    @Column(name = "sapo_variant_id")
    private String sapoVariantId;

```

- [ ] **Step 4: Compile to confirm no errors**

Run: `./mvnw compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/entity/SapoSyncStatus.java src/main/java/com/fashionvista/backend/entity/Product.java src/main/java/com/fashionvista/backend/entity/ProductVariant.java
git commit -m "feat(sapo): add sync-status columns to Product/ProductVariant"
```

---

### Task 2: Repository query methods

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/repository/ProductRepository.java`
- Modify: `src/main/java/com/fashionvista/backend/repository/ProductVariantRepository.java`

**Interfaces:**
- Consumes: `Product`, `ProductVariant`, `SapoSyncStatus` from Task 1.
- Produces: `ProductRepository.findBySapoSyncStatusNot(SapoSyncStatus)` — consumed by Task 10's migrate endpoint. `ProductVariantRepository.findBySapoVariantId(String)` — consumed by Task 9's webhook controller.

This codebase has no precedent for isolated repository-layer tests (confirmed: no `@DataJpaTest` usage anywhere in `src/test`); these two derived-query methods are exercised indirectly through Task 9/10's controller tests, consistent with how every other repository method in this codebase is tested (via mocks at the consuming layer, not directly).

- [ ] **Step 1: Add `findBySapoSyncStatusNot` to `ProductRepository`**

Replace the full contents of `src/main/java/com/fashionvista/backend/repository/ProductRepository.java`:

```java
package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.SapoSyncStatus;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    @Query("select distinct p from Product p left join fetch p.images where p.id in :ids")
    List<Product> findAllWithImagesByIdIn(List<Long> ids);

    List<Product> findBySapoSyncStatusNot(SapoSyncStatus sapoSyncStatus);
}
```

- [ ] **Step 2: Add `findBySapoVariantId` to `ProductVariantRepository`**

In `src/main/java/com/fashionvista/backend/repository/ProductVariantRepository.java`, add this method right after `findBySku`:

```java
    Optional<ProductVariant> findBySapoVariantId(String sapoVariantId);

```

- [ ] **Step 3: Compile to confirm no errors**

Run: `./mvnw compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/fashionvista/backend/repository/ProductRepository.java src/main/java/com/fashionvista/backend/repository/ProductVariantRepository.java
git commit -m "feat(sapo): add repository lookups for sync status and variant id"
```

---

### Task 3: `SapoOutboundProperties` config + application.properties

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/config/SapoOutboundProperties.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: `SapoOutboundProperties` with `getApiKey()`, `getApiSecret()`, `getStoreDomain()`, `getWebhookSecret()` — consumed by Task 5 (`SapoApiClient`) and Task 8 (`SapoHmacVerifier`). Registered via `@EnableConfigurationProperties` in Task 9's `SapoWebhookSecurityConfig`.

- [ ] **Step 1: Create `SapoOutboundProperties`**

```java
package com.fashionvista.backend.integration.sapo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("sapo.outbound")
public class SapoOutboundProperties {
    private String apiKey;
    private String apiSecret;
    private String storeDomain;
    private String webhookSecret;
}
```

- [ ] **Step 2: Add property defaults to `application.properties`**

Append to the end of `src/main/resources/application.properties` (after the existing `# Sapo Integration` block):

```properties

# Sapo Outbound Integration (Website -> Sapo, and inventory webhook Sapo -> Website)
sapo.outbound.api-key=${SAPO_OUTBOUND_API_KEY:dev-key-change-me}
sapo.outbound.api-secret=${SAPO_OUTBOUND_API_SECRET:dev-secret-change-me}
sapo.outbound.store-domain=${SAPO_STORE_DOMAIN:your-store.mysapo.net}
sapo.outbound.webhook-secret=${SAPO_WEBHOOK_SECRET:dev-webhook-secret-change-me}
```

- [ ] **Step 3: Compile to confirm no errors**

Run: `./mvnw compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/config/SapoOutboundProperties.java src/main/resources/application.properties
git commit -m "feat(sapo): add outbound integration properties"
```

---

### Task 4: DTOs

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductPushRequest.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductPushResponse.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoMigrationSummary.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoWebhookInventoryPayload.java`

**Interfaces:**
- Produces: `SapoProductPushRequest` (with nested `Product`/`Variant` builders), `SapoProductPushResponse` (with nested `Product`/`Variant`, mutable + Jackson-deserializable), `SapoMigrationSummary`, `SapoWebhookInventoryPayload` — consumed by Task 5 (`SapoApiClient`), Task 6 (`SapoProductSyncService`), Task 9 (webhook controller), Task 10 (admin controller).

Plain DTOs with no branching logic — no dedicated unit test; correctness is exercised through the consuming services' tests (Tasks 5, 6, 9, 10).

- [ ] **Step 1: Create `SapoProductPushRequest`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoProductPushRequest {

    Product product;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {
        String name;
        List<Variant> variants;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Variant {
        String id;
        String option1;
        String option2;
        String price;
        String sku;

        @JsonProperty("inventory_management")
        String inventoryManagement;

        @JsonProperty("inventory_quantity")
        Integer inventoryQuantity;
    }
}
```

- [ ] **Step 2: Create `SapoProductPushResponse`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoProductPushResponse {

    private Product product;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        private String id;
        private List<Variant> variants;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Variant {
        private String id;
        private String sku;
    }
}
```

- [ ] **Step 3: Create `SapoMigrationSummary`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoMigrationSummary {
    int totalScanned;
    int succeeded;
    int failed;
    List<Long> failedProductIds;
}
```

- [ ] **Step 4: Create `SapoWebhookInventoryPayload`**

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoWebhookInventoryPayload {

    @JsonProperty("variant_id")
    private Long variantId;

    private String sku;

    @JsonProperty("inventory_quantity")
    private Integer inventoryQuantity;
}
```

- [ ] **Step 5: Compile to confirm no errors**

Run: `./mvnw compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/dto/
git commit -m "feat(sapo): add outbound sync and webhook DTOs"
```

---

### Task 5: `SapoApiClient`

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java`

**Interfaces:**
- Consumes: `SapoOutboundProperties` (Task 3), `SapoProductPushRequest`/`SapoProductPushResponse` (Task 4).
- Produces: `SapoApiClient.createProduct(SapoProductPushRequest): SapoProductPushResponse`, `SapoApiClient.updateProduct(String sapoProductId, SapoProductPushRequest): SapoProductPushResponse` — consumed by Task 6's `SapoProductSyncService`.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SapoApiClientTest {

    private SapoProductPushRequest sampleRequest() {
        SapoProductPushRequest.Variant variant = SapoProductPushRequest.Variant.builder()
                .sku("SKU1")
                .price("100000")
                .inventoryManagement("bizweb")
                .inventoryQuantity(5)
                .build();
        SapoProductPushRequest.Product product = SapoProductPushRequest.Product.builder()
                .name("Test Product")
                .variants(List.of(variant))
                .build();
        return SapoProductPushRequest.builder().product(product).build();
    }

    @Test
    void createProduct_PostsToProductsJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"product\":{\"id\":\"999\",\"variants\":[{\"id\":\"888\",\"sku\":\"SKU1\"}]}}",
                        MediaType.APPLICATION_JSON));

        SapoProductPushResponse response = client.createProduct(sampleRequest());

        server.verify();
        assertEquals("999", response.getProduct().getId());
        assertEquals("888", response.getProduct().getVariants().get(0).getId());
    }

    @Test
    void updateProduct_PutsToProductByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products/999.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"product\":{\"id\":\"999\",\"variants\":[{\"id\":\"888\",\"sku\":\"SKU1\"}]}}",
                        MediaType.APPLICATION_JSON));

        SapoProductPushResponse response = client.updateProduct("999", sampleRequest());

        server.verify();
        assertEquals("999", response.getProduct().getId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoApiClientTest -q`
Expected: FAIL — compilation error, `SapoApiClient` does not exist

- [ ] **Step 3: Write the implementation**

```java
package com.fashionvista.backend.integration.sapo.client;

import com.fashionvista.backend.integration.sapo.config.SapoOutboundProperties;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SapoApiClient {

    private static final int TIMEOUT_MILLIS = 5000;

    private final RestClient restClient;

    public SapoApiClient(SapoOutboundProperties properties) {
        this(buildRestClient(properties));
    }

    SapoApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(SapoOutboundProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TIMEOUT_MILLIS);

        String credentials = properties.getApiKey() + ":" + properties.getApiSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl("https://" + properties.getStoreDomain())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public SapoProductPushResponse createProduct(SapoProductPushRequest request) {
        return restClient.post()
                .uri("/admin/products.json")
                .body(request)
                .retrieve()
                .body(SapoProductPushResponse.class);
    }

    public SapoProductPushResponse updateProduct(String sapoProductId, SapoProductPushRequest request) {
        return restClient.put()
                .uri("/admin/products/{id}.json", sapoProductId)
                .body(request)
                .retrieve()
                .body(SapoProductPushResponse.class);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoApiClientTest -q`
Expected: PASS — 2 tests

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java
git commit -m "feat(sapo): add SapoApiClient for outbound product push"
```

---

### Task 6: `SapoProductSyncService`

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/service/SapoProductSyncService.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/service/SapoProductSyncServiceTest.java`

**Interfaces:**
- Consumes: `SapoApiClient.createProduct/updateProduct` (Task 5), `ProductRepository.save` (existing), `Product`/`ProductVariant`/`SapoSyncStatus` (Task 1).
- Produces: `SapoProductSyncService.pushProduct(Product): void` — consumed by Task 7's `ProductServiceImpl` hooks and Task 10's admin controller.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class SapoProductSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private ProductRepository productRepository;

    private SapoProductSyncService service;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        service = new SapoProductSyncService(sapoApiClient, productRepository);

        product = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(BigDecimal.valueOf(100000))
                .sapoSyncStatus(SapoSyncStatus.PENDING)
                .variants(new ArrayList<>())
                .build();

        variant = ProductVariant.builder()
                .id(10L)
                .product(product)
                .size("M")
                .color("Red")
                .sku("SKU-M-RED")
                .price(BigDecimal.valueOf(100000))
                .stock(5)
                .build();
        product.getVariants().add(variant);
    }

    @Test
    void pushProduct_NoSapoProductId_CallsCreateAndAppliesSyncedStatus() {
        SapoProductPushResponse.Variant responseVariant = new SapoProductPushResponse.Variant();
        responseVariant.setId("sapo-var-1");
        responseVariant.setSku("SKU-M-RED");

        SapoProductPushResponse.Product responseProduct = new SapoProductPushResponse.Product();
        responseProduct.setId("sapo-prod-1");
        responseProduct.setVariants(List.of(responseVariant));

        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(responseProduct);

        when(sapoApiClient.createProduct(any(SapoProductPushRequest.class))).thenReturn(response);

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.SYNCED, product.getSapoSyncStatus());
        assertEquals("sapo-prod-1", product.getSapoProductId());
        assertEquals("sapo-var-1", variant.getSapoVariantId());
        assertNull(product.getSapoSyncError());
        verify(productRepository).save(product);
    }

    @Test
    void pushProduct_HasSapoProductId_CallsUpdate() {
        product.setSapoProductId("existing-sapo-id");

        SapoProductPushResponse.Product responseProduct = new SapoProductPushResponse.Product();
        responseProduct.setId("existing-sapo-id");
        responseProduct.setVariants(List.of());

        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(responseProduct);

        when(sapoApiClient.updateProduct(eq("existing-sapo-id"), any(SapoProductPushRequest.class)))
                .thenReturn(response);

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.SYNCED, product.getSapoSyncStatus());
        verify(sapoApiClient).updateProduct(eq("existing-sapo-id"), any(SapoProductPushRequest.class));
    }

    @Test
    void pushProduct_ClientThrows_MarksFailedAndStillSaves() {
        when(sapoApiClient.createProduct(any(SapoProductPushRequest.class)))
                .thenThrow(new RestClientException("Sapo unreachable"));

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.FAILED, product.getSapoSyncStatus());
        assertEquals("Sapo unreachable", product.getSapoSyncError());
        verify(productRepository).save(product);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoProductSyncServiceTest -q`
Expected: FAIL — compilation error, `SapoProductSyncService` does not exist

- [ ] **Step 3: Write the implementation**

```java
package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class SapoProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoProductSyncService.class);
    private static final String INVENTORY_MANAGEMENT_BIZWEB = "bizweb";

    private final SapoApiClient sapoApiClient;
    private final ProductRepository productRepository;

    @Transactional
    public void pushProduct(Product product) {
        SapoProductPushRequest request = buildRequest(product);
        try {
            SapoProductPushResponse response = product.getSapoProductId() == null
                    ? sapoApiClient.createProduct(request)
                    : sapoApiClient.updateProduct(product.getSapoProductId(), request);
            applySuccess(product, response);
        } catch (RestClientException ex) {
            log.error("Sapo sync failed for product id={}: {}", product.getId(), ex.getMessage(), ex);
            applyFailure(product, ex.getMessage());
        }
        productRepository.save(product);
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

    private void applySuccess(Product product, SapoProductPushResponse response) {
        if (response == null || response.getProduct() == null) {
            applyFailure(product, "Sapo trả về phản hồi rỗng.");
            return;
        }

        product.setSapoProductId(response.getProduct().getId());
        product.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        product.setSapoSyncError(null);
        product.setSapoSyncedAt(LocalDateTime.now());

        List<SapoProductPushResponse.Variant> returnedVariants = response.getProduct().getVariants();
        List<ProductVariant> localVariants = product.getVariants();
        if (returnedVariants != null) {
            int count = Math.min(returnedVariants.size(), localVariants.size());
            for (int i = 0; i < count; i++) {
                localVariants.get(i).setSapoVariantId(returnedVariants.get(i).getId());
            }
        }
    }

    private void applyFailure(Product product, String errorMessage) {
        product.setSapoSyncStatus(SapoSyncStatus.FAILED);
        product.setSapoSyncError(errorMessage);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoProductSyncServiceTest -q`
Expected: PASS — 3 tests

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/service/SapoProductSyncService.java src/test/java/com/fashionvista/backend/integration/sapo/service/SapoProductSyncServiceTest.java
git commit -m "feat(sapo): add SapoProductSyncService (create/update/fallback + failure handling)"
```

---

### Task 7: Hook into `ProductServiceImpl`

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/service/impl/ProductServiceImpl.java`
- Modify: `src/test/java/com/fashionvista/backend/service/impl/ProductServiceImplTest.java`

**Interfaces:**
- Consumes: `SapoProductSyncService.pushProduct(Product)` (Task 6).

- [ ] **Step 1: Add the failing `@Mock` field to the existing test**

In `src/test/java/com/fashionvista/backend/service/impl/ProductServiceImplTest.java`, add after the `cloudinaryService` mock field (line 40):

```java
    @Mock
    private com.fashionvista.backend.integration.sapo.service.SapoProductSyncService sapoProductSyncService;
```

- [ ] **Step 2: Run test to verify it still compiles/passes before the hook is wired**

Run: `./mvnw test -Dtest=ProductServiceImplTest -q`
Expected: PASS — the extra unused `@Mock` field doesn't break anything yet (Mockito allows unused mocks); this confirms the baseline before the constructor change.

- [ ] **Step 3: Add the constructor field to `ProductServiceImpl`**

In `src/main/java/com/fashionvista/backend/service/impl/ProductServiceImpl.java`, add the import:

```java
import com.fashionvista.backend.integration.sapo.service.SapoProductSyncService;
```

Add the field after `cloudinaryService` (line 51):

```java
    private final SapoProductSyncService sapoProductSyncService;
```

- [ ] **Step 4: Hook `createProduct` — push right before the final return**

Replace lines 204-206:

```java
                    saved = productRepository.save(saved);
                }
            }

            return getProductBySlug(saved.getSlug());
```

with:

```java
                    saved = productRepository.save(saved);
                }
            }

            sapoProductSyncService.pushProduct(saved);

            return getProductBySlug(saved.getSlug());
```

- [ ] **Step 5: Hook `updateProduct` — push right after the final save, before the return**

Replace lines 336-337:

```java
            productRepository.save(product);
            return toDetailDto(product);
```

with:

```java
            productRepository.save(product);
            sapoProductSyncService.pushProduct(product);
            return toDetailDto(product);
```

- [ ] **Step 6: Run the full test suite to verify the existing tests still pass**

Run: `./mvnw test -Dtest=ProductServiceImplTest -q`
Expected: PASS — all existing tests pass unchanged (`sapoProductSyncService.pushProduct(...)` is a void call on a Mockito mock, which no-ops by default)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fashionvista/backend/service/impl/ProductServiceImpl.java src/test/java/com/fashionvista/backend/service/impl/ProductServiceImplTest.java
git commit -m "feat(sapo): hook outbound sync into product create/update flows"
```

---

### Task 8: `SapoHmacVerifier`

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/webhook/SapoHmacVerifier.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/webhook/SapoHmacVerifierTest.java`

**Interfaces:**
- Consumes: `SapoOutboundProperties.getWebhookSecret()` (Task 3).
- Produces: `SapoHmacVerifier.isValid(byte[] rawBody, String receivedSignature): boolean` — consumed by Task 9's `SapoWebhookController`.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.integration.sapo.config.SapoOutboundProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoHmacVerifierTest {

    @Mock
    private SapoOutboundProperties properties;

    private SapoHmacVerifier verifier;

    @BeforeEach
    void setUp() {
        when(properties.getWebhookSecret()).thenReturn("test-webhook-secret");
        verifier = new SapoHmacVerifier(properties);
    }

    @Test
    void isValid_CorrectSignature_ReturnsTrue() throws Exception {
        byte[] body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, "test-webhook-secret");

        assertTrue(verifier.isValid(body, signature));
    }

    @Test
    void isValid_TamperedBody_ReturnsFalse() throws Exception {
        byte[] originalBody = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(originalBody, "test-webhook-secret");

        byte[] tamperedBody = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":9999}"
                .getBytes(StandardCharsets.UTF_8);

        assertFalse(verifier.isValid(tamperedBody, signature));
    }

    @Test
    void isValid_WrongSecret_ReturnsFalse() throws Exception {
        byte[] body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, "a-different-secret");

        assertFalse(verifier.isValid(body, signature));
    }

    @Test
    void isValid_NullSignature_ReturnsFalse() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertFalse(verifier.isValid(body, null));
    }

    private String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoHmacVerifierTest -q`
Expected: FAIL — compilation error, `SapoHmacVerifier` does not exist

- [ ] **Step 3: Write the implementation**

```java
package com.fashionvista.backend.integration.sapo.webhook;

import com.fashionvista.backend.integration.sapo.config.SapoOutboundProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SapoHmacVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SapoOutboundProperties properties;

    public boolean isValid(byte[] rawBody, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computed = mac.doFinal(rawBody);
            String computedSignature = Base64.getEncoder().encodeToString(computed);
            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoHmacVerifierTest -q`
Expected: PASS — 4 tests

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/webhook/SapoHmacVerifier.java src/test/java/com/fashionvista/backend/integration/sapo/webhook/SapoHmacVerifierTest.java
git commit -m "feat(sapo): add HMAC-SHA256 raw-body verifier for inventory webhook"
```

---

### Task 9: `SapoWebhookController` + security config

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/webhook/SapoWebhookController.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/config/SapoWebhookSecurityConfig.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/webhook/SapoWebhookControllerTest.java`

**Interfaces:**
- Consumes: `SapoHmacVerifier.isValid` (Task 8), `ProductVariantRepository.findBySapoVariantId`/`findBySku` (Task 2 / existing), `SapoWebhookInventoryPayload` (Task 4).
- Produces: `POST /webhook/sapo/inventory-update` endpoint, permit-all + stateless security matcher.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.integration.sapo.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SapoWebhookControllerTest {

    @Mock
    private SapoHmacVerifier hmacVerifier;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SapoWebhookController controller =
                new SapoWebhookController(hmacVerifier, productVariantRepository, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void handleInventoryUpdate_ValidSignature_UpdatesStock() throws Exception {
        String body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":42}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);

        ProductVariant variant = ProductVariant.builder().id(1L).sku("SKU1").stock(0).build();
        when(productVariantRepository.findBySapoVariantId("123")).thenReturn(Optional.of(variant));

        mockMvc.perform(post("/webhook/sapo/inventory-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(productVariantRepository).save(variant);
        Assertions.assertEquals(42, variant.getStock());
    }

    @Test
    void handleInventoryUpdate_InvalidSignature_Returns401AndSkipsDb() throws Exception {
        String body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":42}";
        when(hmacVerifier.isValid(any(byte[].class), eq("bad-signature"))).thenReturn(false);

        mockMvc.perform(post("/webhook/sapo/inventory-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "bad-signature")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(productVariantRepository, never()).findBySapoVariantId(anyString());
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void handleInventoryUpdate_VariantNotFound_Returns200AndSkipsUpdate() throws Exception {
        String body = "{\"variant_id\":999,\"sku\":\"UNKNOWN-SKU\",\"inventory_quantity\":10}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);
        when(productVariantRepository.findBySapoVariantId("999")).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("UNKNOWN-SKU")).thenReturn(Optional.empty());

        mockMvc.perform(post("/webhook/sapo/inventory-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(productVariantRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SapoWebhookControllerTest -q`
Expected: FAIL — compilation error, `SapoWebhookController` does not exist

- [ ] **Step 3: Write `SapoWebhookController`**

```java
package com.fashionvista.backend.integration.sapo.webhook;

import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.dto.SapoWebhookInventoryPayload;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/sapo")
@RequiredArgsConstructor
public class SapoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SapoWebhookController.class);

    private final SapoHmacVerifier hmacVerifier;
    private final ProductVariantRepository productVariantRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/inventory-update")
    @Transactional
    public ResponseEntity<Void> handleInventoryUpdate(
            HttpServletRequest request,
            @RequestHeader(value = "X-Sapo-Hmac-SHA256", required = false) String signature) throws IOException {

        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!hmacVerifier.isValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SapoWebhookInventoryPayload payload = objectMapper.readValue(rawBody, SapoWebhookInventoryPayload.class);

        ProductVariant variant = resolveVariant(payload);
        if (variant == null) {
            log.warn("Sapo inventory webhook: no local variant found for variantId={} sku={}",
                    payload.getVariantId(), payload.getSku());
            return ResponseEntity.ok().build();
        }

        variant.setStock(payload.getInventoryQuantity());
        productVariantRepository.save(variant);
        return ResponseEntity.ok().build();
    }

    private ProductVariant resolveVariant(SapoWebhookInventoryPayload payload) {
        if (payload.getVariantId() != null) {
            Optional<ProductVariant> bySapoId = productVariantRepository
                    .findBySapoVariantId(String.valueOf(payload.getVariantId()));
            if (bySapoId.isPresent()) {
                return bySapoId.get();
            }
        }
        if (payload.getSku() != null) {
            return productVariantRepository.findBySku(payload.getSku()).orElse(null);
        }
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SapoWebhookControllerTest -q`
Expected: PASS — 3 tests

- [ ] **Step 5: Write `SapoWebhookSecurityConfig`**

```java
package com.fashionvista.backend.integration.sapo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(SapoOutboundProperties.class)
public class SapoWebhookSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain sapoWebhookFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/webhook/sapo/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

(`@Order(2)` — confirmed no other `@Order`-annotated `SecurityFilterChain` bean exists in `com.fashionvista.backend.config` besides the existing `SapoSecurityConfig`'s `@Order(1)`, so `2` does not collide.)

- [ ] **Step 6: Compile to confirm no errors**

Run: `./mvnw compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/webhook/SapoWebhookController.java src/main/java/com/fashionvista/backend/integration/sapo/config/SapoWebhookSecurityConfig.java src/test/java/com/fashionvista/backend/integration/sapo/webhook/SapoWebhookControllerTest.java
git commit -m "feat(sapo): add inventory webhook endpoint with HMAC verification"
```

---

### Task 10: `AdminSapoSyncController` (retry-sync + migrate)

**Files:**
- Create: `src/main/java/com/fashionvista/backend/controller/AdminSapoSyncController.java`
- Test: `src/test/java/com/fashionvista/backend/controller/AdminSapoSyncControllerTest.java`

**Interfaces:**
- Consumes: `ProductRepository.findById`/`findBySapoSyncStatusNot` (existing / Task 2), `SapoProductSyncService.pushProduct` (Task 6).
- Produces: `POST /api/admin/sapo/products/{id}/retry-sync`, `POST /api/admin/sapo/products/migrate` (admin-role-protected).

- [ ] **Step 1: Write the failing test**

```java
package com.fashionvista.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.service.SapoProductSyncService;
import com.fashionvista.backend.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminSapoSyncControllerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SapoProductSyncService sapoProductSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminSapoSyncController controller = new AdminSapoSyncController(productRepository, sapoProductSyncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void retrySync_ProductExists_CallsPushProduct() throws Exception {
        Product product = Product.builder().id(1L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(post("/api/admin/sapo/products/1/retry-sync"))
                .andExpect(status().isOk());

        verify(sapoProductSyncService).pushProduct(product);
    }

    @Test
    void retrySync_ProductNotFound_Returns400() throws Exception {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/sapo/products/99/retry-sync"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrate_MixedResults_ReturnsSummary() throws Exception {
        Product succeeding = Product.builder().id(1L).sapoSyncStatus(SapoSyncStatus.PENDING).build();
        Product failing = Product.builder().id(2L).sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(productRepository.findBySapoSyncStatusNot(SapoSyncStatus.SYNCED))
                .thenReturn(List.of(succeeding, failing));

        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            if (p.getId().equals(1L)) {
                p.setSapoSyncStatus(SapoSyncStatus.SYNCED);
            } else {
                p.setSapoSyncStatus(SapoSyncStatus.FAILED);
            }
            return null;
        }).when(sapoProductSyncService).pushProduct(any(Product.class));

        mockMvc.perform(post("/api/admin/sapo/products/migrate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScanned").value(2))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.failedProductIds[0]").value(2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AdminSapoSyncControllerTest -q`
Expected: FAIL — compilation error, `AdminSapoSyncController` does not exist

- [ ] **Step 3: Write the implementation**

```java
package com.fashionvista.backend.controller;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.dto.SapoMigrationSummary;
import com.fashionvista.backend.integration.sapo.service.SapoProductSyncService;
import com.fashionvista.backend.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sapo/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSapoSyncController {

    private final ProductRepository productRepository;
    private final SapoProductSyncService sapoProductSyncService;

    @PostMapping("/{id}/retry-sync")
    public void retrySync(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        sapoProductSyncService.pushProduct(product);
    }

    @PostMapping("/migrate")
    public SapoMigrationSummary migrate() {
        List<Product> pending = productRepository.findBySapoSyncStatusNot(SapoSyncStatus.SYNCED);
        int succeeded = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Product product : pending) {
            sapoProductSyncService.pushProduct(product);
            if (product.getSapoSyncStatus() == SapoSyncStatus.SYNCED) {
                succeeded++;
            } else {
                failedIds.add(product.getId());
            }
        }

        return SapoMigrationSummary.builder()
                .totalScanned(pending.size())
                .succeeded(succeeded)
                .failed(failedIds.size())
                .failedProductIds(failedIds)
                .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AdminSapoSyncControllerTest -q`
Expected: PASS — 3 tests

- [ ] **Step 5: Run the full test suite**

Run: `./mvnw test -q`
Expected: `BUILD SUCCESS` — every test in the module passes, including all pre-existing tests

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/controller/AdminSapoSyncController.java src/test/java/com/fashionvista/backend/controller/AdminSapoSyncControllerTest.java
git commit -m "feat(sapo): add admin retry-sync and migrate endpoints"
```
