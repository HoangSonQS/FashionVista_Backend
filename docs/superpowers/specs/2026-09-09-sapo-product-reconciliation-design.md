# Sapo Product Reconciliation — Design Spec

**Date:** 2026-09-09
**Scope (this spec):** A `PRODUCT` implementation of the existing
`SapoSyncHealthCheck` framework (see
`docs/superpowers/specs/2026-08-06-sapo-sync-health-design.md`), detecting
data drift between FashionVista's local product catalog and Sapo's hosted
catalog. New admin remediation actions and a dedicated Product admin UI are
explicitly out of scope — see Non-goals.

## Context

Product outbound sync already exists: `SapoProductSyncService.pushProduct()`
pushes a local `Product` (name + variants) to Sapo via
`SapoApiClient.createProduct()` / `updateProduct()`, and records the result
on `Product.sapoSyncStatus` / `sapoSyncError` / `sapoSyncedAt`. What does not
exist is any check for drift *after* that push succeeds — Sapo's catalog can
still diverge from FashionVista's over time (a product edited directly in
Sapo's back office, a product manually deleted or duplicated on Sapo's side,
a push that reported success but silently didn't apply). The generic
`SapoSyncHealthCheck` reconciliation framework already built for Inventory,
Order, and Voucher (`SyncHealthScheduler`, `SyncDiscrepancyService`, the
`sync_discrepancy` table, and the admin `GET /discrepancies` /
`POST .../resolve` / `POST /run-now` endpoints) is domain-agnostic by
design — adding Product means writing one new
`@Component implements SapoSyncHealthCheck` class, no changes to the
scheduler, email, or the generic parts of the admin API.

This spec was developed against the real code in this repo (entities, DTOs,
`SapoProductSyncService`, `SapoApiClient`) rather than assumption, per this
repo's Sapo Integration Rule — every design decision below cites the
existing code it is grounded in.

## Goals

- Detect four kinds of Product/Sapo drift, every 30 minutes (reusing the
  existing `SyncHealthScheduler` cadence):
  - A local product that should be on Sapo but isn't (`MISSING_ON_SAPO`).
  - A Sapo product with no corresponding local product
    (`EXCESS_ON_SAPO`).
  - The same local SKU appearing under more than one Sapo product id
    (`DUPLICATE_ON_SAPO`).
  - A local product whose synced fields (name, or a variant's effective
    price/size/color) no longer match what Sapo has (`VALUE_MISMATCH`).
- Reuse every existing piece of the framework unchanged: `SyncDiscrepancy`
  persistence/dedup, the email alert, and the generic discrepancy-listing
  and resolve/run-now admin endpoints.
- Protect against alert storms from a broken or empty read on *either* side
  (Sapo or local) producing a flood of false discrepancies in one run.

## Non-goals

- **New admin remediation actions for `PRODUCT`.** The existing
  `AdminSyncHealthController.pushToSapo()` / `pullFromSapo()` will continue
  to reject `PRODUCT` discrepancies with the existing generic
  `IllegalArgumentException` ("Domain không hỗ trợ ...") fallback — no new
  `else if (domain == SyncDomain.PRODUCT)` branch is added by this spec.
  Admins can still see Product discrepancies (`GET /discrepancies?domain=PRODUCT`,
  already generic), mark them resolved once fixed by hand or via the
  existing `/api/admin/sapo/products/{id}/retry-sync` endpoint (already
  generic `POST /discrepancies/{id}/resolve`), and force an off-cycle check
  (already generic `POST /run-now`) — all three work today with zero code
  changes because they operate on `SyncDomain` generically. A dedicated
  "push this product now" / "delete the Sapo duplicate" admin action is
  deferred to a future sub-project, mirroring how the original sync-health
  spec deferred Product's admin UI entirely.
- **A persisted scan cursor.** Every run does a fresh full local scan
  (`ProductRepository.findBySapoProductIdIsNotNull()`) and a fresh full
  Sapo catalog scan. No `lastFullScanAt` bookkeeping — YAGNI at current
  catalog size; revisit if the full scan becomes too slow or rate-limited.
- **Real-time push-triggered detection.** Unlike Inventory's real-time
  `pushStock()`, Product detection only runs on the 30-minute cycle. The
  existing `pushProduct()` call path already reports its own success/failure
  synchronously via `sapoSyncStatus`; this spec only covers *after-the-fact*
  drift that a successful push wouldn't have caught.
- **Comparing `stock`, `isVisible`/`status`, or `compareAtPrice`.**
  `SapoProductSyncService.buildRequest()` (via `toVariant()`) never sends
  publish/visibility state, and never sends `compareAtPrice` — only `name`
  and each variant's `price` (with its zero/null → product-price fallback),
  `sku`, `option1` (size), `option2` (color), and `inventoryQuantity` are
  pushed. Sapo holds no authoritative value for the fields this spec
  excludes, so comparing them would only ever manufacture false positives.
  Stock drift is already covered by `InventorySyncHealthCheck`.

## Architecture

```
integration/sapo/synchealth/
  ProductSyncHealthCheck.java     # NEW: implements SapoSyncHealthCheck, domain() = PRODUCT
integration/sapo/client/
  SapoApiClient.java              # MODIFIED: + listProducts(page, limit), + countProducts()
integration/sapo/dto/
  SapoProductListResponse.java    # NEW: catalog-listing response DTO
repository/
  ProductRepository.java          # MODIFIED: + findBySapoProductIdIsNotNull()
entity/
  SyncDomain.java                 # MODIFIED: + PRODUCT
  DiscrepancyType.java            # MODIFIED: + MISSING_ON_SAPO, EXCESS_ON_SAPO, DUPLICATE_ON_SAPO
db/migration/
  V15__update_sync_discrepancy_domain_constraint.sql  # NEW
```

`ProductSyncHealthCheck` is picked up automatically by `SyncHealthScheduler`
(`List<SapoSyncHealthCheck>` injection) — no change to the scheduler class
itself.

## Data Model

**`DiscrepancyType`** (`entity/DiscrepancyType.java`) currently has only
`NOT_SYNCED, VALUE_MISMATCH, SYNC_FAILED`. This spec adds three values:

```java
public enum DiscrepancyType {
    NOT_SYNCED,
    VALUE_MISMATCH,
    SYNC_FAILED,
    MISSING_ON_SAPO,
    EXCESS_ON_SAPO,
    DUPLICATE_ON_SAPO
}
```

`VALUE_MISMATCH` is reused as-is for Product (same semantics: synced fields
differ). `NOT_SYNCED` is deliberately *not* reused for the "local product
never reached Sapo" case — `NOT_SYNCED` is Inventory/Order's "never
attempted a push" signal; a local product with `sapoProductId == null` is
out of this check's scope entirely (see Matching Logic), so this ambiguity
doesn't actually arise. `MISSING_ON_SAPO` covers only the case where a push
was believed to succeed (`sapoProductId != null`) but Sapo doesn't have it
anymore.

**`SyncDomain`** (`entity/SyncDomain.java`) currently has only
`INVENTORY, ORDER, VOUCHER`. This spec adds `PRODUCT`:

```java
public enum SyncDomain {
    INVENTORY,
    ORDER,
    VOUCHER,
    PRODUCT
}
```

**Migration.** `SyncDiscrepancy.domain` has a DB-level `CHECK` constraint
(confirmed: `V14__update_sync_discrepancy_domain_constraint.sql`, added
specifically because Hibernate's JPA-level `@Enumerated(EnumType.STRING)`
does not update a pre-existing DB `CHECK`). `discrepancy_type` has **no**
equivalent DB-level `CHECK` constraint anywhere in
`src/main/resources/db/migration/` (confirmed via search) — only `domain`
needs a migration. New file, mirroring `V14` exactly:

```sql
-- Cập nhật constraint domain cho bảng sync_discrepancy để hỗ trợ PRODUCT

ALTER TABLE sync_discrepancy DROP CONSTRAINT IF EXISTS sync_discrepancy_domain_check;

ALTER TABLE sync_discrepancy ADD CONSTRAINT sync_discrepancy_domain_check CHECK (
    domain IN ('INVENTORY','ORDER','VOUCHER','PRODUCT')
);
```

## Sapo API additions

Two new read-only calls on `SapoApiClient`, following the existing
`getProduct(String sapoProductId)` style (`restClient.get()...retrieve()`),
against Sapo's real Admin API (`/admin/products.json`):

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
```

**`SapoProductListResponse`** (new DTO, `Jackson`, `@JsonIgnoreProperties(ignoreUnknown = true)`
throughout, following `SapoProductPushResponse`'s style):

```java
public class SapoProductListResponse {
    private List<Product> products;

    public static class Product {
        private String id;
        private String name;
        @JsonProperty("published_on")
        private String publishedOn;   // nullable — confirmed nullable on Sapo's real schema
        private List<Variant> variants;
    }

    public static class Variant {
        private String id;
        private String sku;
        private String price;
        private String option1;   // size
        private String option2;   // color
    }
}
```

`SapoProductCountResponse` is a one-field `{ "count": <long> }` DTO, same
pattern.

**Hand-rolled retry (no new dependency).** `SapoApiClient.java` currently
has **no** retry/backoff infrastructure anywhere (confirmed: no
`@Retryable`/`RetryTemplate`/manual loop in any of its 15 existing methods;
no retry library in `pom.xml`). Per this repo's "never install new
dependencies without discussion" rule, this spec adds a small private
helper on `SapoApiClient` — no library:

```java
private static final int MAX_ATTEMPTS = 3;
private static final long[] BACKOFF_MS = {500, 1000};

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

3 attempts total, delays 500ms then 1000ms between attempts (exponential
backoff — `BACKOFF_MS` has exactly `MAX_ATTEMPTS - 1` entries, one per gap
between attempts, since the last attempt is never followed by a sleep).
Only `listProducts()` and
`countProducts()` use it in this spec — existing write methods
(`createProduct`, `updateProduct`, etc.) are untouched; retrying a
non-idempotent `POST` is a separate concern this spec does not take on.

**Pagination safety cap.** `ProductSyncHealthCheck` calls `listProducts()`
in a loop until a page returns fewer than `limit` products, capped at 50
pages (`limit=250` per page, matching Sapo's typical max page size → cap of
12,500 products scanned per run). Hitting the cap logs a WARN
("Sapo product catalog scan hit the 50-page safety cap — catalog may be
larger than scanned") and proceeds with whatever was fetched, rather than
looping forever against a misbehaving API.

## Repository addition

```java
@Query("select distinct p from Product p left join fetch p.variants where p.sapoProductId is not null")
List<Product> findBySapoProductIdIsNotNull();
```

Mirrors `findBySapoSyncStatusNot`'s existing JPQL style exactly (same
join-fetch-variants shape). This is the **only** local read `ProductSyncHealthCheck`
performs — scope is limited to local products that have already been synced
at least once (`sapoProductId != null`); a local product that has never
been pushed is `SapoProductSyncService`'s problem (retry job), not this
check's.

## Matching / Diffing Logic

`ProductSyncHealthCheck.checkAll()`:

1. **Read local side:** `products = productRepository.findBySapoProductIdIsNotNull()`.
2. **Read Sapo side:** `sapoCount = sapoApiClient.countProducts()`; then
   page through `listProducts()` (capped per above) to build the full list
   `sapoProducts`.
3. **Sanity guard** (see below) — may short-circuit to `List.of()` here.
4. **Build two Sapo-side indices** from `sapoProducts`:
   - `sapoProductsById: Map<String, SapoProductListResponse.Product>` —
     keyed by Sapo product id.
   - `skuToSapoProductIds: Map<String, List<String>>` — for every variant
     SKU across every fetched Sapo product, the list of distinct Sapo
     product ids that SKU appears under. Built from the **full** Sapo
     catalog scan (not filtered to in-scope local products) since it needs
     to see every Sapo-side occurrence of a SKU to detect duplicates.
5. **Build the in-scope local SKU index:** `localInScopeSkus: Set<String>`
   — every variant SKU (plus each product's own top-level `sku` field)
   across `products` (the local, already-synced set only). This is
   deliberately scoped to *synced* local products, not all local products —
   using the full local catalog here would hide a real `EXCESS_ON_SAPO`
   signal behind products that simply haven't been pushed yet.
6. **Per local product** (`products`), compare against
   `sapoProductsById.get(product.getSapoProductId())`:
   - **Not found** → one `MISSING_ON_SAPO` candidate.
     `entityId = product.getId()`, `entityLabel = product.getSku()`,
     `details = "Sapo không có sản phẩm với id=" + product.getSapoProductId()`.
   - **Found, but this local product's own SKU (or any of its variant
     SKUs) maps to more than one Sapo product id in `skuToSapoProductIds`**
     → one `DUPLICATE_ON_SAPO` candidate. Neutral wording, no assumed
     cause: `entityId = product.getId()`, `entityLabel = product.getSku()`,
     `details = "SKU xuất hiện trên nhiều sản phẩm Sapo (id: " + <ids> + ")"`.
   - **Found, one match** → compare fields; if **any** differ, one
     *aggregated* `VALUE_MISMATCH` candidate for the whole product (not one
     per field, not one per variant):
     - `product.getName()` vs Sapo product's `name`.
     - Per matched variant (joined by SKU): effective price — mirroring
       `SapoProductSyncService.toVariant()`'s exact fallback
       (`variant.getPrice() != null && > 0 ? variant.getPrice() : variant.getProduct().getPrice()`)
       compared via `BigDecimal.compareTo()` against
       `new BigDecimal(sapoVariant.getPrice())` (never `.equals()` /
       string equality — `"199.00"` and `"199"` must compare equal).
     - Per matched variant: `size` vs `option1`, `color` vs `option2`
       (plain string equality).
     - `entityId = product.getId()`, `entityLabel = product.getSku()`,
       `details` lists which field(s) differed and old/new values.
7. **Per Sapo product** (`sapoProducts`), for `EXCESS_ON_SAPO`: flag it if
   its id is **not** a value of any local product's `sapoProductId` **and**
   none of its variant SKUs appear in `localInScopeSkus`.
   `entityLabel = sapoProduct.getName()`. `entityId` requires parsing:
   Sapo product ids are `String` end-to-end in this codebase
   (`Product.sapoProductId`, `SapoProductPushResponse.Product.id` are both
   `String`), but `DiscrepancyCandidate.entityId` is `Long`. Parse with
   `Long.parseLong(sapoProduct.getId())`; if that throws
   `NumberFormatException` (Sapo ids are numeric in practice, but this
   guards the parse rather than assuming), log a WARN and **skip** that one
   candidate — don't fail the whole run over one malformed id.
8. Each per-product/per-Sapo-product comparison is wrapped in its own
   `try/catch (RuntimeException)`: log and `continue`, matching
   `InventorySyncHealthCheck`'s "one bad item doesn't stop the batch"
   pattern.

## Sanity Guard (symmetric, all-or-nothing)

A broken read on *either* side must not flood the discrepancy table with
false positives. Before returning candidates, check both directions in the
same run:

- **Sapo-side risk:** if the count of `MISSING_ON_SAPO` candidates that
  *would* be produced exceeds 50% of `products.size()` (the in-scope local
  count), the Sapo-side read is untrustworthy (empty/erroneous catalog
  response, wrong store, auth pointed at the wrong tenant, etc.).
- **Local-side risk:** if the count of `EXCESS_ON_SAPO` candidates that
  *would* be produced exceeds 50% of `sapoProducts.size()`, the local-side
  read is untrustworthy (e.g. `findBySapoProductIdIsNotNull()` returning
  near-empty due to a filter bug, wrong DB connection/schema, or a
  transient local issue) — the mirror image of the Sapo-side risk, and
  previously unaddressed until this spec.

Either condition tripping means **one side's data cannot be trusted for
this entire run** — `checkAll()` logs one WARN naming which direction
tripped and its counts, and returns `List.of()` **for every candidate
type computed that run**, not just the type that tripped the threshold (a
genuine `VALUE_MISMATCH` computed in the same run as a tripped guard is
also discarded, since the same broken read that produced the mass
`MISSING_ON_SAPO`/`EXCESS_ON_SAPO` count could just as easily have
corrupted the matches that looked fine).

**Mechanism, precisely:** `checkAll()` returns an empty list — it does
**not** throw. `SyncHealthScheduler.runNow()` (confirmed:
`integration/sapo/synchealth/SyncHealthScheduler.java:28-45`) calls
`syncDiscrepancyService.reconcile(check.domain(), candidates)`
unconditionally right after a non-throwing `checkAll()`, inside the same
try-block. `SyncDiscrepancyService.reconcile()` (confirmed:
`SyncDiscrepancyService.java:22-53`) only *upserts* discrepancies present
in the candidate list it's given — it never auto-resolves an existing open
discrepancy just because that cycle's candidate list is empty or smaller
than before. So `reconcile()` is still called with `List.of()` when the
guard trips, but that call is a safe no-op: no new rows, no emails, and
critically, **no existing genuine open discrepancy gets silently
resolved** by an empty run. The guard doesn't need to (and must not) block
the `reconcile()` call itself — returning an empty candidate list from
`checkAll()` is sufficient and requires no change to the shared
`SyncHealthScheduler`.

## Error Handling

- **Global abort** (propagates as a thrown `RuntimeException` from
  `checkAll()`, caught by `SyncHealthScheduler`'s existing per-domain
  try/catch, which logs and continues with the other domains that cycle):
  `countProducts()` or `listProducts()` failing after all 3 retry attempts
  are exhausted; `productRepository.findBySapoProductIdIsNotNull()`
  throwing.
- **Per-item failure** (caught individually inside the comparison loop,
  logged, skip-and-continue — does not abort the run): one product's
  comparison throwing (e.g. a malformed Sapo variant price string that
  fails `new BigDecimal(...)`), or one `EXCESS_ON_SAPO` id failing to
  parse (see step 7 above).
- **Sanity guard trip** (not an error — a deliberate empty result, see
  above): logged as WARN, not ERROR; `checkAll()` returns `List.of()`,
  does not throw.

## Testing

Unit tests for `ProductSyncHealthCheck`, mocking `ProductRepository` and
`SapoApiClient` (JUnit 5 + Mockito + AssertJ, matching
`InventorySyncHealthCheckTest`'s conventions):

1. **One test per discrepancy type:**
   - `checkAll_LocalProductNotOnSapo_ReturnsMissingOnSapoCandidate`
   - `checkAll_SapoProductNotLinkedAndSkuUnmatched_ReturnsExcessOnSapoCandidate`
   - `checkAll_SkuFoundUnderMultipleSapoIds_ReturnsDuplicateOnSapoCandidate`
     (assert `details` uses neutral wording, not an assumed cause)
   - `checkAll_NameOrVariantFieldDiffers_ReturnsOneValueMismatchCandidatePerProduct`
     (multiple differing fields on one product → exactly one candidate)
   - `checkAll_AllDataMatches_ReturnsEmptyList`

2. **Comparison edge cases:**
   - `checkAll_PriceStringsNumericallyEqualButFormattedDifferently_NoMismatch`
     (`"199.00"` vs `"199.0"`/`"199"` → no mismatch; confirms
     `BigDecimal.compareTo`, not string equality)
   - `checkAll_VariantPriceIsZero_FallsBackToProductPrice` (mirrors
     `SapoProductSyncService.toVariant()`'s exact fallback)
   - `checkAll_StockDiffers_DoesNotProduceCandidate` (deliberate exclusion)
   - `checkAll_PublishedStatusDiffers_DoesNotProduceCandidate` (deliberate
     exclusion — push flow never sends this field)
   - `checkAll_CompareAtPriceDiffers_DoesNotProduceCandidate` (deliberate
     exclusion — push flow never sends this field either)

3. **Sanity-guard tests (symmetric):**
   - `checkAll_SapoCatalogSuspiciouslyEmpty_ReturnsEmptyList` (Sapo-side
     empty/erroneous response with many in-scope local products → guard
     trips, returns `List.of()`, does not throw)
   - `checkAll_LocalCatalogSuspiciouslyEmpty_ReturnsEmptyList` (local-side
     empty/erroneous read with many Sapo products → guard trips
     symmetrically, returns `List.of()`)
   - `checkAll_GuardTrips_DiscardsAllCandidateTypesNotJustTheTriggeringOne`
     (constructs data with both a large false `MISSING_ON_SAPO` count AND
     one genuine `VALUE_MISMATCH` in the same run; asserts both are
     discarded)

4. **Error-handling tests:**
   - `checkAll_ListProductsPageFailsAfterRetriesExhausted_PropagatesException`
   - `checkAll_CountEndpointFailsAfterRetriesExhausted_PropagatesException`
   - `checkAll_OneProductThrowsDuringComparison_SkipsItAndContinuesWithOthers`
   - `checkAll_ExcessCandidateHasNonNumericSapoId_SkipsItAndContinuesWithOthers`

5. **`SapoApiClient.withRetry` tests** (new, since this is brand-new
   hand-rolled logic with no prior test coverage in this codebase):
   - `listProducts_SucceedsOnThirdAttempt_ReturnsResult`
   - `listProducts_FailsAllThreeAttempts_ThrowsLastException`
   - `countProducts_SucceedsFirstAttempt_DoesNotSleep` (verify no
     unnecessary delay on the happy path)

6. **Repository test** (`ProductRepositoryTest.java`, new — adjusted for
   this codebase's confirmed H2/Postgres limitation:
   `Product` uses Postgres-only `jsonb`/`text[]` columns that H2 cannot
   create even under `MODE=PostgreSQL`, so no `@DataJpaTest` is possible
   for any `Product`-persisting test here — see
   `ProductVariantRepositoryTest.java`'s existing comment for the same
   pre-existing, project-wide gap). Mirrors that file's reflection-based
   pattern: reflect on `findBySapoProductIdIsNotNull()`'s `@Query`
   annotation and assert (via `.contains(...)`, not exact match) that the
   JPQL contains `"p.sapoProductId is not null"` and
   `"left join fetch p.variants"`.

7. **DTO test** (`SapoProductListResponseTest.java`, new — mirrors
   `SapoTransactionDtoTest`'s plain-`ObjectMapper`, no-Spring-context
   pattern): deserialize a realistic sample JSON payload matching the
   confirmed real Sapo schema (`products` array; each product has `id`,
   `name`, `published_on`, `variants` array with `id`, `sku`, `price`,
   `option1`, `option2`), including a dedicated case for `published_on`
   being `null`.
