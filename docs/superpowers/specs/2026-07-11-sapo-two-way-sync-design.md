# Sapo Two-Way Sync (Outbound Orchestrator) — Design Spec

**Date:** 2026-07-11
**Scope:** Products + Inventory only, bidirectional. Other entity groups (Orders,
Customers, Vouchers, Suppliers, Purchase Orders, Purchase Returns, Stock Transfers)
granted read/write scopes on Sapo but are **out of scope** for this spec — future work.

## Context

An existing Sapo module (`controller/sapo/*`, shipped 2026-06-20) handles the
**inbound** direction: Sapo calls FashionVista's `/api/sapo/v1/*` endpoints
(pull model, `X-Api-Key` auth) to read products/inventory/customers/vouchers
and push POS orders in.

This spec covers the **opposite, previously unbuilt** direction: FashionVista
Backend acts as orchestrator and:
1. Pushes product/inventory changes made on the website *out* to Sapo's Admin
   REST API (`https://docs.sapo.vn/docs/api/admin-rest/`)
2. Receives a webhook *from* Sapo when inventory changes on their side (e.g.
   in-store POS sale), and updates local stock accordingly

No code for this direction exists yet (`SapoApiClient`/outbound `WebClient` —
none found in the codebase as of this writing).

## Out of scope / explicit non-goals

- OAuth2 token acquisition flow — a static long-lived Sapo access token is
  assumed to already exist (provided out of band, stored as a property/env
  var, like the existing `sapo.api.key` pattern)
- Automatic webhook registration on Sapo (the `POST /admin/webhooks.json`
  call) — done manually once a production webhook URL exists
- Automatic retry job/scheduler for failed syncs — retry is a manual
  admin-triggered endpoint only
- Async outbox/queue infrastructure — sync calls happen inline within the
  admin request; acceptable given current product catalog size
- Entities other than Product/Variant/Inventory (Orders, Customers, Vouchers,
  Suppliers, Purchase Orders/Returns, Stock Transfers)

## Architecture

New package, kept separate from the existing inbound `controller/sapo/`
module:

```
integration/sapo/
  client/SapoApiClient.java            # WebClient wrapper calling out to Sapo Admin REST API
  config/SapoOutboundProperties.java   # access token, base URL, webhook secret
  service/SapoProductSyncService.java  # push Product/Variant create+update
  service/SapoInventorySyncService.java# push absolute stock value
  webhook/SapoWebhookController.java   # receives inventory_levels/update
  webhook/SapoHmacVerifier.java        # raw-body HMAC-SHA256 verification
  dto/
    SapoProductPushRequest.java
    SapoInventoryPushRequest.java
    SapoWebhookInventoryPayload.java
    SapoSyncResult.java
```

## Data model changes

Columns added directly to existing entities (chosen over a separate mapping
table — simpler joins, 1:1 relationship, matches the existing `Order.source`
precedent of adding a nullable column and letting `ddl-auto=update` migrate
it):

- `Product`:
  - `sapoProductId` (String, nullable)
  - `sapoSyncStatus` (enum: `PENDING`, `SYNCED`, `FAILED`; default `PENDING`)
  - `sapoSyncError` (String, nullable)
  - `sapoSyncedAt` (Instant, nullable)
- `ProductVariant`:
  - `sapoVariantId` (String, nullable)

## Outbound flow: Website → Sapo

**Trigger:** synchronous, inline in the admin request — no outbox/queue.
Hooked into `AdminProductController`'s create/update paths and the inventory
update path, after the local DB save succeeds.

**Create:**
1. Save Product/Variant locally first (web DB is source of truth)
2. Call `POST /admin/products.json` via `SapoApiClient` with header
   `X-Sapo-Access-Token`
3. Success → parse returned `product_id`/`variant_id`, update the local
   record: `sapoProductId`, `sapoVariantId`, `sapoSyncStatus=SYNCED`,
   `sapoSyncedAt=now()`
4. Failure (timeout/4xx/5xx) → **no rollback** of the original transaction.
   Set `sapoSyncStatus=FAILED`, `sapoSyncError=<message>`. The admin request
   still returns 200/201 normally.

**Update:**
- If `sapoProductId` present → `PUT /admin/products/{id}.json`
- If absent (never synced, or a prior `FAILED` record) → fall back to the
  create flow automatically, self-healing a prior failure without a separate
  manual step

**Inventory push:** absolute stock value sent (not a delta) — consistent with
the existing inbound convention where Sapo's inventory PUT is also absolute.

**Retry:** manual only, via `POST /api/admin/sapo/products/{id}/retry-sync`
(admin-role-protected). No automatic scanning/retry job in this spec.

**Client behavior (`SapoApiClient`):**
- 5s timeout, no automatic retry (retry is the manual endpoint above)
- Errors logged with the local product ID for traceability
- Exceptions never propagate past `SapoProductSyncService` — always caught,
  recorded as `FAILED`, caller (admin controller) proceeds normally

## Inbound flow: Sapo → Website (inventory webhook)

```
POST /webhook/sapo/inventory-update
```

- Registered on Sapo manually (`topic=inventory_levels/update`) once a
  production URL exists — not automated by this spec
- `SapoHmacVerifier` reads the **raw request body** (not a Jackson-deserialized
  `@RequestBody` object, which reformats the body and breaks the signature),
  computes HMAC-SHA256 with the webhook secret, base64-encodes it, and
  compares against the `X-Sapo-Hmac-SHA256` header. Mismatch → `401`, no
  further processing.
- On verified payload: look up `ProductVariant` by `sapoVariantId`, set local
  stock to the absolute value Sapo sent
- Variant not found → log a warning, still return `200 OK` (Sapo requires a
  200 within 1-2s regardless of business-level outcomes; a missing mapping is
  a data issue, not a protocol failure)
- Security: `/webhook/sapo/**` registered as a separate security matcher
  bypassing the JWT chain, mirroring the existing `SapoSecurityConfig`
  pattern for `/api/sapo/**`, but authenticating via HMAC instead of
  `X-Api-Key`

## Migration script (one-time backfill)

```
POST /api/admin/sapo/products/migrate
```

- Admin-role-protected, not public
- Scans all `Product` rows where `sapoProductId IS NULL` (or
  `sapoSyncStatus != SYNCED`), pushes them sequentially (not parallel, to
  avoid tripping Sapo rate limits) through `SapoProductSyncService.pushProduct()`
- Returns a summary: total scanned, succeeded, failed (with failed product
  IDs)
- Runs synchronously within the request given current catalog size; if the
  catalog grows large enough that this becomes a timeout risk, moving it to a
  background job is a known future improvement, not built now

## Testing plan

- Unit tests for `SapoHmacVerifier` (valid signature, invalid signature,
  tampered body)
- Unit tests for `SapoProductSyncService` create/update/fallback-to-create
  logic, mocking `SapoApiClient`
- Unit tests for failure path: confirm local save survives a `SapoApiClient`
  exception and `sapoSyncStatus=FAILED` is persisted
- Controller test for the webhook endpoint: valid signature updates stock;
  invalid signature returns 401 without touching the DB
- Controller test for the migrate endpoint: mixed success/failure summary

## Known limitations (accepted for this iteration)

- No automatic retry/backoff — a failed sync requires a manual admin action
- No async outbox — a slow/down Sapo API adds latency to the admin request
  (bounded by the 5s client timeout)
- Migration endpoint is synchronous — fine at current catalog size only
- Webhook registration itself is a manual, out-of-band step
