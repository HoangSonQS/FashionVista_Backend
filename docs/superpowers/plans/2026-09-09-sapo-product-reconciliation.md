# Sapo Product Data-Drift Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `PRODUCT` implementation of the existing `SapoSyncHealthCheck` framework that detects four kinds of drift between FashionVista's local product catalog and Sapo's hosted catalog — `MISSING_ON_SAPO`, `EXCESS_ON_SAPO`, `DUPLICATE_ON_SAPO`, `VALUE_MISMATCH` — every 30 minutes, reusing the existing `SyncDiscrepancy` persistence/dedup/email/admin-endpoint pipeline unchanged.

**Architecture:** A new `ProductSyncHealthCheck` (auto-discovered by `SyncHealthScheduler`'s `List<SapoSyncHealthCheck>` injection — no scheduler changes) reads all locally-synced products via a new `ProductRepository` query, fetches Sapo's full product catalog via two new `SapoApiClient` methods (`listProducts`, `countProducts`) that share a new hand-rolled retry/backoff helper, and diffs the two sets. A symmetric 50% sanity guard discards every candidate type for the run if either the missing-rate or the excess-rate looks like a broken API response rather than real drift.

**Tech Stack:** Java 17, Spring Boot 4.0.0, Spring Data JPA, Jackson, Lombok, JUnit 5, Mockito, AssertJ, Spring `RestClient` + `MockRestServiceServer`. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-09-sapo-product-reconciliation-design.md`

## Global Constraints

- No new admin remediation actions for PRODUCT — `AdminSyncHealthController.java` is not touched; its domain branches (INVENTORY/ORDER/VOUCHER) already compile and run unchanged.
- No persisted scan cursor — every run does a fresh full scan of both catalogs.
- No real-time push-triggered detection — detection only happens on the existing 30-minute `SyncHealthScheduler` cycle.
- Comparison explicitly excludes `stock`/`inventoryQuantity`, `isVisible`/`status`/`published_on`, and `compareAtPrice` — none of these are ever sent to Sapo by `SapoProductSyncService`, so drift in them is not this domain's concern.
- Exactly one aggregated `VALUE_MISMATCH` candidate per product, never one per differing field or per variant.
- Zero new Maven dependencies — retry/backoff is hand-rolled (`Thread.sleep` between attempts), matching the repo-wide "never install new dependencies without discussion" rule.
- Sanity guard is symmetric and all-or-nothing: if `MISSING_ON_SAPO` candidates exceed 50% of local in-scope products, OR `EXCESS_ON_SAPO` candidates exceed 50% of fetched Sapo products, log one WARN and return `List.of()` for every candidate type computed that run — not just the type that tripped.
- Any Sapo integration work must be consistent with `docs/sapo-api-reference.md`'s framing: that file is FashionVista's *inbound* spec, not Sapo's docs. The two new outbound endpoints (`GET /admin/products.json`, `GET /admin/products/count.json`) mirror Sapo's existing product-push endpoints already used by `SapoApiClient.getProduct`/`createProduct`, so no new endpoint shape is being invented here.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/fashionvista/backend/entity/DiscrepancyType.java` | **Modify** — add `MISSING_ON_SAPO`, `EXCESS_ON_SAPO`, `DUPLICATE_ON_SAPO` |
| `src/main/java/com/fashionvista/backend/entity/SyncDomain.java` | **Modify** — add `PRODUCT` |
| `src/main/resources/db/migration/V15__update_sync_discrepancy_domain_constraint.sql` | **Create** — widen `sync_discrepancy_domain_check` to include `PRODUCT` |
| `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponse.java` | **Create** — Jackson DTO for Sapo's product-listing response |
| `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductCountResponse.java` | **Create** — Jackson DTO for Sapo's product-count response |
| `src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponseTest.java` | **Create** — plain-`ObjectMapper` deserialization test |
| `src/main/java/com/fashionvista/backend/repository/ProductRepository.java` | **Modify** — add `findBySapoProductIdIsNotNull()` |
| `src/test/java/com/fashionvista/backend/repository/ProductRepositoryTest.java` | **Create** — reflection-based `@Query` pin (H2 cannot host a real `Product` row) |
| `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java` | **Modify** — add `listProducts`, `countProducts`, `withRetry` |
| `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java` | **Modify** — add 3 tests for the new client methods |
| `src/main/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheck.java` | **Create** — the `PRODUCT` domain's `SapoSyncHealthCheck` implementation |
| `src/test/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheckTest.java` | **Create** — 17 Mockito-based tests covering every candidate type, comparison edge case, guard scenario, and error path |

---

### Task 1: Enum additions + migration

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/entity/DiscrepancyType.java`
- Modify: `src/main/java/com/fashionvista/backend/entity/SyncDomain.java`
- Create: `src/main/resources/db/migration/V15__update_sync_discrepancy_domain_constraint.sql`

**Interfaces:**
- Produces: `DiscrepancyType.MISSING_ON_SAPO`, `DiscrepancyType.EXCESS_ON_SAPO`, `DiscrepancyType.DUPLICATE_ON_SAPO`, `SyncDomain.PRODUCT` — every later task in this plan references these four constants by exact name.

This task introduces no new behavior (an enum constant and a migration file are inert until referenced), so there is no test-first cycle. Migrations in this project are never executed by the app itself — `pom.xml` has no Flyway dependency, and the H2 test profile (`src/test/resources/application-test.properties`) builds its schema via Hibernate `ddl-auto=create-drop`, entirely bypassing `db/migration/`. The only honest verification available is a successful compile.

- [ ] **Step 1: Add the three new constants to `DiscrepancyType`**

Replace the full contents of `src/main/java/com/fashionvista/backend/entity/DiscrepancyType.java`:

```java
package com.fashionvista.backend.entity;

public enum DiscrepancyType {
    NOT_SYNCED,
    VALUE_MISMATCH,
    SYNC_FAILED,
    MISSING_ON_SAPO,
    EXCESS_ON_SAPO,
    DUPLICATE_ON_SAPO
}
```

- [ ] **Step 2: Add `PRODUCT` to `SyncDomain`**

Replace the full contents of `src/main/java/com/fashionvista/backend/entity/SyncDomain.java`:

```java
package com.fashionvista.backend.entity;

public enum SyncDomain {
    INVENTORY,
    ORDER,
    VOUCHER,
    PRODUCT
}
```

- [ ] **Step 3: Create the V15 migration**

Create `src/main/resources/db/migration/V15__update_sync_discrepancy_domain_constraint.sql`:

```sql
-- Cập nhật constraint domain cho bảng sync_discrepancy để hỗ trợ PRODUCT

ALTER TABLE sync_discrepancy DROP CONSTRAINT IF EXISTS sync_discrepancy_domain_check;

ALTER TABLE sync_discrepancy ADD CONSTRAINT sync_discrepancy_domain_check CHECK (
    domain IN ('INVENTORY','ORDER','VOUCHER','PRODUCT')
);
```

- [ ] **Step 4: Verify the project compiles**

Run: `./mvnw compile`
Expected: `BUILD SUCCESS`. This is the only meaningful check for this task — there is no Flyway runner and no test harness that touches `db/migration/`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/entity/DiscrepancyType.java src/main/java/com/fashionvista/backend/entity/SyncDomain.java src/main/resources/db/migration/V15__update_sync_discrepancy_domain_constraint.sql
git commit -m "feat(sapo): add PRODUCT domain and discrepancy types for product reconciliation"
```

---

### Task 2: Sapo product-listing DTOs

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponse.java`
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductCountResponse.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponseTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `SapoProductListResponse` (fields: `List<Product> products`; nested `Product`: `String id`, `String name`, `String publishedOn` (JSON `published_on`), `List<Variant> variants`; nested `Variant`: `String id`, `String sku`, `String price`, `String option1`, `String option2`) and `SapoProductCountResponse` (field: `long count`). Task 4 (`SapoApiClient`) and Task 5 (`ProductSyncHealthCheck`) both consume these exact types and field names.

- [ ] **Step 1: Write the failing DTO test**

Create `src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponseTest.java`:

```java
package com.fashionvista.backend.integration.sapo.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SapoProductListResponseTest {

    @Test
    void response_DeserializesProductsWithVariantsAndNullPublishedOn() throws Exception {
        String json = "{\"products\":[{\"id\":\"111\",\"name\":\"Ao thun\",\"published_on\":null,"
                + "\"variants\":[{\"id\":\"222\",\"sku\":\"SKU-1\",\"price\":\"150000\","
                + "\"option1\":\"M\",\"option2\":\"Do\"}]}]}";

        SapoProductListResponse response = new ObjectMapper().readValue(json, SapoProductListResponse.class);

        assertThat(response.getProducts()).hasSize(1);
        SapoProductListResponse.Product product = response.getProducts().get(0);
        assertThat(product.getId()).isEqualTo("111");
        assertThat(product.getName()).isEqualTo("Ao thun");
        assertThat(product.getPublishedOn()).isNull();
        assertThat(product.getVariants()).hasSize(1);
        SapoProductListResponse.Variant variant = product.getVariants().get(0);
        assertThat(variant.getId()).isEqualTo("222");
        assertThat(variant.getSku()).isEqualTo("SKU-1");
        assertThat(variant.getPrice()).isEqualTo("150000");
        assertThat(variant.getOption1()).isEqualTo("M");
        assertThat(variant.getOption2()).isEqualTo("Do");
    }

    @Test
    void response_IgnoresUnknownFieldsAndHandlesEmptyProductsList() throws Exception {
        String json = "{\"products\":[],\"unexpected_field\":true}";

        SapoProductListResponse response = new ObjectMapper().readValue(json, SapoProductListResponse.class);

        assertThat(response.getProducts()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SapoProductListResponseTest`
Expected: compilation failure — `SapoProductListResponse` does not exist yet.

- [ ] **Step 3: Create the two DTOs**

Create `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponse.java`:

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
public class SapoProductListResponse {

    private List<Product> products;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        private String id;
        private String name;

        @JsonProperty("published_on")
        private String publishedOn;

        private List<Variant> variants;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Variant {
        private String id;
        private String sku;
        private String price;
        private String option1;
        private String option2;
    }
}
```

Create `src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductCountResponse.java`:

```java
package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoProductCountResponse {

    private long count;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SapoProductListResponseTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponse.java src/main/java/com/fashionvista/backend/integration/sapo/dto/SapoProductCountResponse.java src/test/java/com/fashionvista/backend/integration/sapo/dto/SapoProductListResponseTest.java
git commit -m "feat(sapo): add DTOs for Sapo product-listing and product-count responses"
```

---

### Task 3: Repository query for synced products

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/repository/ProductRepository.java`
- Test: `src/test/java/com/fashionvista/backend/repository/ProductRepositoryTest.java`

**Interfaces:**
- Consumes: `Product` entity (existing).
- Produces: `ProductRepository.findBySapoProductIdIsNotNull(): List<Product>` — Task 5 calls this exact method to get the local in-scope product set.

- [ ] **Step 1: Write the failing repository test**

Create `src/test/java/com/fashionvista/backend/repository/ProductRepositoryTest.java`:

```java
package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * Regression guard pinning the JPQL for {@link ProductRepository#findBySapoProductIdIsNotNull}.
 * This project's shared H2 test database (see application-test.properties) cannot create the
 * products table because Product uses Postgres-only column types (jsonb, text[]) that H2 does
 * not understand even under MODE=PostgreSQL compatibility - the same pre-existing gap documented
 * in ProductVariantRepositoryTest. A real @DataJpaTest exercising this query end-to-end is
 * therefore not possible here, so this test pins the query text via reflection instead.
 */
class ProductRepositoryTest {

    @Test
    void findBySapoProductIdIsNotNull_isAnnotatedWithExpectedJpql() throws NoSuchMethodException {
        Method method = ProductRepository.class.getMethod("findBySapoProductIdIsNotNull");

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("p.sapoProductId is not null")
                .contains("left join fetch p.variants");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ProductRepositoryTest`
Expected: compilation failure — `findBySapoProductIdIsNotNull` does not exist on `ProductRepository` yet.

- [ ] **Step 3: Add the query method**

Modify `src/main/java/com/fashionvista/backend/repository/ProductRepository.java` — add this method inside the interface, after `findBySapoSyncStatusNot`:

```java
    @Query("select distinct p from Product p left join fetch p.variants where p.sapoProductId is not null")
    List<Product> findBySapoProductIdIsNotNull();
```

The full resulting file:

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

    @Query("select distinct p from Product p left join fetch p.variants where p.sapoSyncStatus <> :sapoSyncStatus")
    List<Product> findBySapoSyncStatusNot(SapoSyncStatus sapoSyncStatus);

    @Query("select distinct p from Product p left join fetch p.variants where p.sapoProductId is not null")
    List<Product> findBySapoProductIdIsNotNull();
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=ProductRepositoryTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/repository/ProductRepository.java src/test/java/com/fashionvista/backend/repository/ProductRepositoryTest.java
git commit -m "feat(sapo): add ProductRepository query for locally-synced products"
```

---

### Task 4: `SapoApiClient` retry helper + product-listing/count methods

**Files:**
- Modify: `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java`
- Modify: `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java`

**Interfaces:**
- Consumes: `SapoProductListResponse`, `SapoProductCountResponse` (Task 2).
- Produces: `SapoApiClient.listProducts(int page, int limit): SapoProductListResponse`, `SapoApiClient.countProducts(): long` — Task 5 calls these exact signatures.

- [ ] **Step 1: Write the failing tests**

Modify `src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java`. First, update the imports block at the top of the file to the following (adds 3 static imports and 2 regular imports versus the current file):

```java
package com.fashionvista.backend.integration.sapo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
```

Then append these 3 test methods immediately before the final closing brace of the class (after `updateDiscountCode_PutsToDiscountCodeByIdAndParsesResponse`):

```java

    @Test
    void listProducts_SucceedsOnThirdAttempt_ReturnsResult() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json?page=1&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json?page=1&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json?page=1&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"products\":[{\"id\":\"111\",\"name\":\"Ao thun\",\"variants\":[]}]}",
                        MediaType.APPLICATION_JSON));

        SapoProductListResponse response = client.listProducts(1, 50);

        server.verify();
        assertEquals(1, response.getProducts().size());
        assertEquals("111", response.getProducts().get(0).getId());
    }

    @Test
    void listProducts_FailsAllThreeAttempts_ThrowsLastException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json?page=1&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json?page=1&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json?page=1&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThrows(RestClientException.class, () -> client.listProducts(1, 50));

        server.verify();
    }

    @Test
    void countProducts_SucceedsFirstAttempt_DoesNotSleep() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products/count.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"count\":42}", MediaType.APPLICATION_JSON));

        long start = System.currentTimeMillis();
        long count = client.countProducts();
        long elapsedMillis = System.currentTimeMillis() - start;

        server.verify();
        assertEquals(42L, count);
        assertTrue(elapsedMillis < 400, "First-attempt success must not sleep; took " + elapsedMillis + "ms");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=SapoApiClientTest`
Expected: compilation failure — `listProducts` and `countProducts` do not exist on `SapoApiClient` yet.

- [ ] **Step 3: Implement the retry helper and the two new methods**

Modify `src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java` in two places.

First, add two imports immediately after the existing `import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;` line (alphabetical order among the dto imports):

```java
import com.fashionvista.backend.integration.sapo.dto.SapoProductCountResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
```

And add one more import immediately after `import java.util.Base64;`:

```java
import java.util.function.Supplier;
```

Second, add two static fields immediately after the existing `private static final int TIMEOUT_MILLIS = 5000;` field declaration:

```java
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {500, 1000};
```

Third, insert the following methods immediately after the end of `updateDiscountCode` (i.e., immediately before the final closing brace of the class):

```java

    public SapoProductListResponse listProducts(int page, int limit) {
        return withRetry(() -> restClient.get()
                .uri("/admin/products.json?page={page}&limit={limit}", page, limit)
                .retrieve()
                .body(SapoProductListResponse.class));
    }

    public long countProducts() {
        return withRetry(() -> restClient.get()
                .uri("/admin/products/count.json")
                .retrieve()
                .body(SapoProductCountResponse.class))
                .getCount();
    }

    private <T> T withRetry(Supplier<T> call) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException ex) {
                lastError = ex;
                if (attempt < MAX_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastError;
                    }
                }
            }
        }
        throw lastError;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=SapoApiClientTest`
Expected: `Tests run: 15, Failures: 0, Errors: 0` (12 existing + 3 new). Note the retry test takes ~1.5s of real sleep — this is expected.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/client/SapoApiClient.java src/test/java/com/fashionvista/backend/integration/sapo/client/SapoApiClientTest.java
git commit -m "feat(sapo): add listProducts/countProducts with hand-rolled retry backoff"
```

---

### Task 5: `ProductSyncHealthCheck`

**Files:**
- Create: `src/main/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheck.java`
- Test: `src/test/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheckTest.java`

**Interfaces:**
- Consumes: `ProductRepository.findBySapoProductIdIsNotNull()` (Task 3), `SapoApiClient.listProducts(int, int)` / `SapoApiClient.countProducts()` (Task 4), `SapoProductListResponse`/`SapoProductCountResponse` (Task 2), `DiscrepancyType.MISSING_ON_SAPO`/`EXCESS_ON_SAPO`/`DUPLICATE_ON_SAPO`/`VALUE_MISMATCH`, `SyncDomain.PRODUCT` (Task 1), existing `DiscrepancyCandidate(Long entityId, String entityLabel, DiscrepancyType discrepancyType, String details)` record, existing `SapoSyncHealthCheck` interface (`SyncDomain domain(); List<DiscrepancyCandidate> checkAll();`).
- Produces: `ProductSyncHealthCheck` — a `@Component` auto-discovered by `SyncHealthScheduler`; no other task depends on its internals.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheckTest.java`:

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSyncHealthCheckTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SapoApiClient sapoApiClient;

    @InjectMocks
    private ProductSyncHealthCheck productSyncHealthCheck;

    private static Product localProduct(Long id, String sapoProductId, String sku, String name, BigDecimal price) {
        return Product.builder()
                .id(id)
                .sapoProductId(sapoProductId)
                .sku(sku)
                .name(name)
                .price(price)
                .variants(new ArrayList<>())
                .build();
    }

    private static ProductVariant localVariant(Product product, String sku, String size, String color,
            BigDecimal price, int stock) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .price(price)
                .stock(stock)
                .build();
        product.getVariants().add(variant);
        return variant;
    }

    private static Product healthyProduct(Long id, String sapoProductId, String sku) {
        Product product = localProduct(id, sapoProductId, sku, "San pham " + sku, new BigDecimal("100000"));
        localVariant(product, sku + "-M", "M", "Trang", new BigDecimal("100000"), 10);
        return product;
    }

    private static SapoProductListResponse.Variant sapoVariant(String id, String sku, String option1,
            String option2, String price) {
        SapoProductListResponse.Variant variant = new SapoProductListResponse.Variant();
        variant.setId(id);
        variant.setSku(sku);
        variant.setOption1(option1);
        variant.setOption2(option2);
        variant.setPrice(price);
        return variant;
    }

    private static SapoProductListResponse.Product sapoProduct(String id, String name, String publishedOn,
            List<SapoProductListResponse.Variant> variants) {
        SapoProductListResponse.Product product = new SapoProductListResponse.Product();
        product.setId(id);
        product.setName(name);
        product.setPublishedOn(publishedOn);
        product.setVariants(variants);
        return product;
    }

    private static SapoProductListResponse.Product matchingSapoProduct(Product localProduct) {
        ProductVariant localVariant = localProduct.getVariants().get(0);
        SapoProductListResponse.Variant variant = sapoVariant(
                "sapo-variant-" + localVariant.getSku(), localVariant.getSku(),
                localVariant.getSize(), localVariant.getColor(), localVariant.getPrice().toPlainString());
        return sapoProduct(localProduct.getSapoProductId(), localProduct.getName(), "2026-01-01T00:00:00",
                List.of(variant));
    }

    private static SapoProductListResponse listResponse(SapoProductListResponse.Product... products) {
        SapoProductListResponse response = new SapoProductListResponse();
        response.setProducts(List.of(products));
        return response;
    }

    @Test
    void checkAll_LocalProductNotOnSapo_ReturnsMissingOnSapoCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");
        Product missing = localProduct(3L, "sapo-999", "SKU-C", "San pham SKU-C", new BigDecimal("100000"));
        localVariant(missing, "SKU-C-M", "M", "Trang", new BigDecimal("100000"), 10);

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB, missing));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB)));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(3L);
        assertThat(candidates.get(0).entityLabel()).isEqualTo("SKU-C");
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.MISSING_ON_SAPO);
        assertThat(candidates.get(0).details()).contains("sapo-999");
    }

    @Test
    void checkAll_SapoProductNotLinkedAndSkuUnmatched_ReturnsExcessOnSapoCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");
        SapoProductListResponse.Product excessSapoProduct = sapoProduct(
                "777", "San pham la", "2026-01-01T00:00:00",
                List.of(sapoVariant("sapo-variant-777", "SKU-UNKNOWN", "M", "Trang", "50000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB));
        when(sapoApiClient.countProducts()).thenReturn(3L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB), excessSapoProduct));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(777L);
        assertThat(candidates.get(0).entityLabel()).isEqualTo("San pham la");
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.EXCESS_ON_SAPO);
    }

    @Test
    void checkAll_SkuFoundUnderMultipleSapoIds_ReturnsDuplicateOnSapoCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");
        Product duplicateSubject = localProduct(3L, "3", "SKU-C", "San pham SKU-C", new BigDecimal("100000"));
        localVariant(duplicateSubject, "SKU-C", "M", "Trang", new BigDecimal("100000"), 10);

        SapoProductListResponse.Product sapoDup1 = sapoProduct("3", "San pham SKU-C", "2026-01-01T00:00:00",
                List.of(sapoVariant("v1", "SKU-C", "M", "Trang", "100000")));
        SapoProductListResponse.Product sapoDup2 = sapoProduct("4", "San pham SKU-C ban sao", "2026-01-01T00:00:00",
                List.of(sapoVariant("v2", "SKU-C", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull())
                .thenReturn(List.of(healthyA, healthyB, duplicateSubject));
        when(sapoApiClient.countProducts()).thenReturn(4L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB), sapoDup1, sapoDup2));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(3L);
        assertThat(candidates.get(0).entityLabel()).isEqualTo("SKU-C");
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.DUPLICATE_ON_SAPO);
        assertThat(candidates.get(0).details()).contains("3").contains("4");
    }

    @Test
    void checkAll_NameOrVariantFieldDiffers_ReturnsOneValueMismatchCandidatePerProduct() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product mismatchSubject = healthyProduct(2L, "sapo-2", "SKU-B");
        SapoProductListResponse.Product sapoMismatch = sapoProduct(
                "sapo-2", "Ten khac voi local", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, mismatchSubject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoMismatch));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(2L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.VALUE_MISMATCH);
        assertThat(candidates.get(0).details()).contains("Ten khac voi local");
    }

    @Test
    void checkAll_AllDataMatches_ReturnsEmptyList() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB)));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_PriceStringsNumericallyEqualButFormattedDifferently_NoMismatch() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("100000.00"));
        localVariant(subject, "SKU-B-M", "M", "Trang", new BigDecimal("100000.00"), 10);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_VariantPriceIsZero_FallsBackToProductPrice() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("150000"));
        localVariant(subject, "SKU-B-M", "M", "Trang", BigDecimal.ZERO, 10);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "150000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_StockDiffers_DoesNotProduceCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("100000"));
        localVariant(subject, "SKU-B-M", "M", "Trang", new BigDecimal("100000"), 999);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_PublishedStatusDiffers_DoesNotProduceCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = healthyProduct(2L, "sapo-2", "SKU-B");
        subject.setIsVisible(false);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", subject.getName(), null,
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_CompareAtPriceDiffers_DoesNotProduceCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = healthyProduct(2L, "sapo-2", "SKU-B");
        subject.setCompareAtPrice(new BigDecimal("999999"));
        subject.getVariants().get(0).setCompareAtPrice(new BigDecimal("888888"));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(subject)));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_SapoCatalogSuspiciouslyEmpty_ReturnsEmptyList() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB));
        when(sapoApiClient.countProducts()).thenReturn(0L);

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_LocalCatalogSuspiciouslyEmpty_ReturnsEmptyList() {
        SapoProductListResponse.Product sapoOnly1 = sapoProduct("501", "San pham la 1", "2026-01-01T00:00:00",
                List.of(sapoVariant("v1", "SKU-X1", "M", "Trang", "100000")));
        SapoProductListResponse.Product sapoOnly2 = sapoProduct("502", "San pham la 2", "2026-01-01T00:00:00",
                List.of(sapoVariant("v2", "SKU-X2", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of());
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(listResponse(sapoOnly1, sapoOnly2));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_GuardTrips_DiscardsAllCandidateTypesNotJustTheTriggeringOne() {
        Product missingA = localProduct(1L, "sapo-1", "SKU-A", "San pham SKU-A", new BigDecimal("100000"));
        localVariant(missingA, "SKU-A-M", "M", "Trang", new BigDecimal("100000"), 10);
        Product missingB = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("100000"));
        localVariant(missingB, "SKU-B-M", "M", "Trang", new BigDecimal("100000"), 10);
        Product mismatchSubject = healthyProduct(3L, "sapo-3", "SKU-C");

        SapoProductListResponse.Product sapoMismatch = sapoProduct(
                "sapo-3", "Ten khac voi local", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-c", "SKU-C-M", "M", "Trang", "100000")));
        SapoProductListResponse.Product sapoExcess = sapoProduct("999", "San pham la", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-excess", "SKU-UNKNOWN", "M", "Trang", "50000")));

        when(productRepository.findBySapoProductIdIsNotNull())
                .thenReturn(List.of(missingA, missingB, mismatchSubject));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(listResponse(sapoMismatch, sapoExcess));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_ListProductsPageFailsAfterRetriesExhausted_PropagatesException() {
        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of());
        when(sapoApiClient.countProducts()).thenReturn(10L);
        when(sapoApiClient.listProducts(1, 50)).thenThrow(new RuntimeException("Sapo unreachable"));

        assertThrows(RuntimeException.class, () -> productSyncHealthCheck.checkAll());
    }

    @Test
    void checkAll_CountEndpointFailsAfterRetriesExhausted_PropagatesException() {
        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of());
        when(sapoApiClient.countProducts()).thenThrow(new RuntimeException("Sapo unreachable"));

        assertThrows(RuntimeException.class, () -> productSyncHealthCheck.checkAll());
    }

    @Test
    void checkAll_OneProductThrowsDuringComparison_SkipsItAndContinuesWithOthers() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");

        Product brokenProduct = Product.builder()
                .id(2L).sapoProductId("sapo-2").sku("SKU-B").name("San pham SKU-B")
                .price(new BigDecimal("100000")).variants(new ArrayList<>())
                .build();
        ProductVariant brokenVariant = ProductVariant.builder()
                .product(null).sku("SKU-B-M").size("M").color("Trang")
                .price(BigDecimal.ZERO).stock(10)
                .build();
        brokenProduct.getVariants().add(brokenVariant);

        SapoProductListResponse.Product sapoBroken = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, brokenProduct));
        when(sapoApiClient.countProducts()).thenReturn(2L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoBroken));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_ExcessCandidateHasNonNumericSapoId_SkipsItAndContinuesWithOthers() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        SapoProductListResponse.Product excessNonNumeric = sapoProduct(
                "not-a-number", "San pham la khong hop le", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-nn", "SKU-UNKNOWN-1", "M", "Trang", "50000")));
        SapoProductListResponse.Product excessNumeric = sapoProduct(
                "888", "San pham la hop le", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-n", "SKU-UNKNOWN-2", "M", "Trang", "60000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA));
        when(sapoApiClient.countProducts()).thenReturn(3L);
        when(sapoApiClient.listProducts(1, 50)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), excessNonNumeric, excessNumeric));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(888L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.EXCESS_ON_SAPO);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=ProductSyncHealthCheckTest`
Expected: compilation failure — `ProductSyncHealthCheck` does not exist yet.

- [ ] **Step 3: Implement `ProductSyncHealthCheck`**

Create `src/main/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheck.java`:

```java
package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductSyncHealthCheck implements SapoSyncHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncHealthCheck.class);
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 500;
    private static final double SANITY_GUARD_THRESHOLD = 0.5;

    private final ProductRepository productRepository;
    private final SapoApiClient sapoApiClient;

    @Override
    public SyncDomain domain() {
        return SyncDomain.PRODUCT;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscrepancyCandidate> checkAll() {
        List<Product> localProducts = productRepository.findBySapoProductIdIsNotNull();
        List<SapoProductListResponse.Product> sapoProducts = fetchAllSapoProducts();

        List<DiscrepancyCandidate> missingOnSapo = checkMissingOnSapo(localProducts, sapoProducts);
        List<DiscrepancyCandidate> excessOnSapo = checkExcessOnSapo(localProducts, sapoProducts);
        List<DiscrepancyCandidate> duplicateOnSapo = checkDuplicateOnSapo(localProducts, sapoProducts);
        List<DiscrepancyCandidate> valueMismatch = checkValueMismatch(localProducts, sapoProducts);

        if (tripsSanityGuard(localProducts.size(), sapoProducts.size(), missingOnSapo.size(), excessOnSapo.size())) {
            return List.of();
        }

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        candidates.addAll(missingOnSapo);
        candidates.addAll(excessOnSapo);
        candidates.addAll(duplicateOnSapo);
        candidates.addAll(valueMismatch);
        return candidates;
    }

    private List<SapoProductListResponse.Product> fetchAllSapoProducts() {
        long sapoCount = sapoApiClient.countProducts();
        int totalPages = (int) Math.min(MAX_PAGES, Math.ceil((double) sapoCount / PAGE_SIZE));
        List<SapoProductListResponse.Product> sapoProducts = new ArrayList<>();
        for (int page = 1; page <= totalPages; page++) {
            SapoProductListResponse response = sapoApiClient.listProducts(page, PAGE_SIZE);
            if (response != null && response.getProducts() != null) {
                sapoProducts.addAll(response.getProducts());
            }
        }
        return sapoProducts;
    }

    private boolean tripsSanityGuard(int localCount, int sapoCount, int missingCount, int excessCount) {
        if (localCount > 0 && missingCount > localCount * SANITY_GUARD_THRESHOLD) {
            log.warn("Sapo product sync-health sanity guard tripped: {} of {} local products missing on Sapo "
                    + "(> {}%). Discarding all candidates for this run.",
                    missingCount, localCount, (int) (SANITY_GUARD_THRESHOLD * 100));
            return true;
        }
        if (sapoCount > 0 && excessCount > sapoCount * SANITY_GUARD_THRESHOLD) {
            log.warn("Sapo product sync-health sanity guard tripped: {} of {} Sapo products excess/unmatched "
                    + "(> {}%). Discarding all candidates for this run.",
                    excessCount, sapoCount, (int) (SANITY_GUARD_THRESHOLD * 100));
            return true;
        }
        return false;
    }

    private Map<String, SapoProductListResponse.Product> indexById(
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> byId = new HashMap<>();
        for (SapoProductListResponse.Product product : sapoProducts) {
            if (product.getId() != null) {
                byId.put(product.getId(), product);
            }
        }
        return byId;
    }

    private Map<String, List<String>> indexSkuToSapoProductIds(
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, List<String>> skuToIds = new HashMap<>();
        for (SapoProductListResponse.Product product : sapoProducts) {
            if (product.getVariants() == null) {
                continue;
            }
            for (SapoProductListResponse.Variant variant : product.getVariants()) {
                if (variant.getSku() == null) {
                    continue;
                }
                skuToIds.computeIfAbsent(variant.getSku(), key -> new ArrayList<>()).add(product.getId());
            }
        }
        return skuToIds;
    }

    private Set<String> localInScopeSkus(List<Product> localProducts) {
        Set<String> skus = new HashSet<>();
        for (Product product : localProducts) {
            if (product.getSku() != null) {
                skus.add(product.getSku());
            }
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getSku() != null) {
                    skus.add(variant.getSku());
                }
            }
        }
        return skus;
    }

    private List<DiscrepancyCandidate> checkMissingOnSapo(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> sapoProductsById = indexById(sapoProducts);
        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (Product product : localProducts) {
            try {
                if (!sapoProductsById.containsKey(product.getSapoProductId())) {
                    candidates.add(new DiscrepancyCandidate(
                            product.getId(),
                            product.getSku(),
                            DiscrepancyType.MISSING_ON_SAPO,
                            "Sapo không có sản phẩm với id=" + product.getSapoProductId()));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health MISSING_ON_SAPO check failed for product id={}: {}",
                        product.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private List<DiscrepancyCandidate> checkExcessOnSapo(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Set<String> localSapoProductIds = localProducts.stream()
                .map(Product::getSapoProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> localSkus = localInScopeSkus(localProducts);

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (SapoProductListResponse.Product sapoProduct : sapoProducts) {
            try {
                boolean linkedLocally = localSapoProductIds.contains(sapoProduct.getId());
                boolean skuMatchedLocally = sapoProduct.getVariants() != null
                        && sapoProduct.getVariants().stream()
                                .anyMatch(variant -> variant.getSku() != null
                                        && localSkus.contains(variant.getSku()));
                if (!linkedLocally && !skuMatchedLocally) {
                    Long entityId = Long.parseLong(sapoProduct.getId());
                    candidates.add(new DiscrepancyCandidate(
                            entityId,
                            sapoProduct.getName(),
                            DiscrepancyType.EXCESS_ON_SAPO,
                            "Sản phẩm chỉ tồn tại trên Sapo, không có trong hệ thống nội bộ"));
                }
            } catch (NumberFormatException ex) {
                log.warn("Sapo product sync-health EXCESS_ON_SAPO check: Sapo product id='{}' is not numeric, "
                        + "skipping candidate: {}", sapoProduct.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health EXCESS_ON_SAPO check failed for Sapo product id={}: {}",
                        sapoProduct.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private List<DiscrepancyCandidate> checkDuplicateOnSapo(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> sapoProductsById = indexById(sapoProducts);
        Map<String, List<String>> skuToSapoProductIds = indexSkuToSapoProductIds(sapoProducts);

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (Product product : localProducts) {
            try {
                SapoProductListResponse.Product matched = sapoProductsById.get(product.getSapoProductId());
                if (matched == null || product.getSku() == null) {
                    continue;
                }
                List<String> sapoProductIdsForSku = skuToSapoProductIds.get(product.getSku());
                if (sapoProductIdsForSku != null && sapoProductIdsForSku.size() > 1) {
                    candidates.add(new DiscrepancyCandidate(
                            product.getId(),
                            product.getSku(),
                            DiscrepancyType.DUPLICATE_ON_SAPO,
                            "SKU xuất hiện trên nhiều sản phẩm Sapo (id: "
                                    + String.join(", ", sapoProductIdsForSku) + ")"));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health DUPLICATE_ON_SAPO check failed for product id={}: {}",
                        product.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private List<DiscrepancyCandidate> checkValueMismatch(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> sapoProductsById = indexById(sapoProducts);
        Map<String, List<String>> skuToSapoProductIds = indexSkuToSapoProductIds(sapoProducts);

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (Product product : localProducts) {
            try {
                SapoProductListResponse.Product matched = sapoProductsById.get(product.getSapoProductId());
                if (matched == null) {
                    continue;
                }
                if (product.getSku() != null) {
                    List<String> idsForSku = skuToSapoProductIds.get(product.getSku());
                    if (idsForSku != null && idsForSku.size() > 1) {
                        continue;
                    }
                }

                String mismatchDetails = findFieldMismatch(product, matched);
                if (mismatchDetails != null) {
                    candidates.add(new DiscrepancyCandidate(
                            product.getId(),
                            product.getSku(),
                            DiscrepancyType.VALUE_MISMATCH,
                            mismatchDetails));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health VALUE_MISMATCH check failed for product id={}: {}",
                        product.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private String findFieldMismatch(Product product, SapoProductListResponse.Product sapoProduct) {
        if (!Objects.equals(product.getName(), sapoProduct.getName())) {
            return "local name='" + product.getName() + "' vs sapo name='" + sapoProduct.getName() + "'";
        }

        Map<String, SapoProductListResponse.Variant> sapoVariantsBySku = new HashMap<>();
        if (sapoProduct.getVariants() != null) {
            for (SapoProductListResponse.Variant variant : sapoProduct.getVariants()) {
                if (variant.getSku() != null) {
                    sapoVariantsBySku.put(variant.getSku(), variant);
                }
            }
        }

        for (ProductVariant variant : product.getVariants()) {
            SapoProductListResponse.Variant sapoVariant = sapoVariantsBySku.get(variant.getSku());
            if (sapoVariant == null) {
                continue;
            }

            if (!Objects.equals(variant.getSize(), sapoVariant.getOption1())) {
                return "variant sku=" + variant.getSku() + " local size='" + variant.getSize()
                        + "' vs sapo option1='" + sapoVariant.getOption1() + "'";
            }
            if (!Objects.equals(variant.getColor(), sapoVariant.getOption2())) {
                return "variant sku=" + variant.getSku() + " local color='" + variant.getColor()
                        + "' vs sapo option2='" + sapoVariant.getOption2() + "'";
            }

            BigDecimal effectivePrice = (variant.getPrice() != null
                    && variant.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    ? variant.getPrice()
                    : variant.getProduct().getPrice();
            if (checkPriceMismatch(effectivePrice, sapoVariant.getPrice())) {
                return "variant sku=" + variant.getSku() + " local effectivePrice=" + effectivePrice
                        + " vs sapo price='" + sapoVariant.getPrice() + "'";
            }
        }

        return null;
    }

    private boolean checkPriceMismatch(BigDecimal localPrice, String remotePrice) {
        if (localPrice == null || remotePrice == null) {
            return localPrice != null || remotePrice != null;
        }
        try {
            return localPrice.compareTo(new BigDecimal(remotePrice)) != 0;
        } catch (NumberFormatException ex) {
            log.warn("Could not parse remote price '{}' for mismatch check: {}", remotePrice, ex.getMessage());
            return true;
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=ProductSyncHealthCheckTest`
Expected: `Tests run: 17, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full test suite**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`, no regressions in `SyncHealthScheduler`, `AdminSyncHealthController`, or any other existing test.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheck.java src/test/java/com/fashionvista/backend/integration/sapo/synchealth/ProductSyncHealthCheckTest.java
git commit -m "feat(sapo): add ProductSyncHealthCheck for PRODUCT domain drift detection"
```

---

## Self-Review

**1. Spec coverage:**
- `MISSING_ON_SAPO`, `EXCESS_ON_SAPO`, `DUPLICATE_ON_SAPO`, `VALUE_MISMATCH` enum values → Task 1.
- `SyncDomain.PRODUCT` → Task 1.
- V15 migration widening the domain constraint → Task 1.
- `SapoProductListResponse`/`SapoProductCountResponse` DTOs → Task 2.
- `ProductRepository.findBySapoProductIdIsNotNull()` → Task 3.
- `SapoApiClient.listProducts`/`countProducts` + hand-rolled 3-attempt/500ms-1000ms retry → Task 4.
- Full 8-step matching/diffing algorithm (local read, Sapo read via paging, sanity guard, per-product missing/duplicate/mismatch checks, per-Sapo-product excess check) → Task 5.
- Symmetric all-or-nothing sanity guard → Task 5 (`tripsSanityGuard`), tested by `checkAll_SapoCatalogSuspiciouslyEmpty_ReturnsEmptyList`, `checkAll_LocalCatalogSuspiciouslyEmpty_ReturnsEmptyList`, `checkAll_GuardTrips_DiscardsAllCandidateTypesNotJustTheTriggeringOne`.
- Global-abort vs. per-item error handling → Task 5, tested by `checkAll_ListProductsPageFailsAfterRetriesExhausted_PropagatesException`, `checkAll_CountEndpointFailsAfterRetriesExhausted_PropagatesException`, `checkAll_OneProductThrowsDuringComparison_SkipsItAndContinuesWithOthers`, `checkAll_ExcessCandidateHasNonNumericSapoId_SkipsItAndContinuesWithOthers`.
- Non-goals (no admin remediation action, no `AdminSyncHealthController` change, stock/isVisible/compareAtPrice excluded from comparison) → verified by omission (no controller file touched) and by tests `checkAll_StockDiffers_DoesNotProduceCandidate`, `checkAll_PublishedStatusDiffers_DoesNotProduceCandidate`, `checkAll_CompareAtPriceDiffers_DoesNotProduceCandidate`.
- All ~24 spec test names are covered: the DTO test (2 methods), repository test (1), `SapoApiClient` retry tests (3), and 17 `ProductSyncHealthCheck` tests. No spec requirement is without a task.

**2. Placeholder scan:** No "TBD"/"TODO", no "add appropriate error handling," no "similar to Task N" references — every step has complete, runnable code. Checked.

**3. Type consistency:**
- `DiscrepancyCandidate(Long entityId, String entityLabel, DiscrepancyType discrepancyType, String details)` used identically in Task 5 to the existing record definition.
- `SapoProductListResponse.Product`/`Variant` field names (`id`, `name`, `publishedOn`, `variants`, `sku`, `price`, `option1`, `option2`) defined in Task 2 are used with the exact same getter/setter names throughout Tasks 4 and 5.
- `ProductRepository.findBySapoProductIdIsNotNull(): List<Product>` defined in Task 3 is called with that exact name and no arguments in Task 5.
- `SapoApiClient.listProducts(int page, int limit): SapoProductListResponse` and `SapoApiClient.countProducts(): long` defined in Task 4 are called with matching signatures in Task 5 (`sapoApiClient.listProducts(page, PAGE_SIZE)`, `sapoApiClient.countProducts()`).
- `SyncDomain.PRODUCT` and the three new `DiscrepancyType` constants from Task 1 are referenced by exact name in Task 5. Checked, consistent.

No gaps found; no corrections were necessary.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-09-sapo-product-reconciliation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
