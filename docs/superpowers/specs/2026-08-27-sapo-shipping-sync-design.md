# Sapo Shipping Sync — Design Spec

**Date:** 2026-08-27
**Scope (this spec):** Outbound-only Sapo Fulfillment sync for the Shipping domain —
the sixth of the 7-domain Sapo integration decomposition (Product, Order, Inventory,
Customer done or in progress; Ledger remains after this).

## Context

FashionVista's Sapo integration is being built out domain by domain (see
`docs/superpowers/specs/2026-08-06-sapo-sync-health-design.md` for the original
7-domain decomposition). Product, Order, and Inventory are done and in production.
Voucher is implemented, pending a backend-repo merge. Customer was just designed and
implemented. This spec covers **Shipping**: pushing FashionVista's GHN shipment
lifecycle (create / delivered / returned / cancelled) to Sapo as Fulfillment records
on the corresponding Sapo order.

Sapo's real Fulfillment API (verified against
[support.sapo.vn](https://support.sapo.vn/fulfillment), not just this repo's own
inbound spec at `docs/sapo-api-reference.md`) is a sub-resource of Order:
`/admin/orders/{order_id}/fulfillments...json`, with `POST` create,
`POST .../complete.json`, and `POST .../cancel.json` endpoints. This maps naturally
onto FashionVista's existing `ShippingServiceImpl`, which already has `createShipping`,
`cancelShipping`, and a GHN `handleWebhook` handler — none of which touch Sapo today.

Two existing gaps in the codebase block a clean mapping and are addressed as part of
this design (not a separate refactor): `Order` has no persisted `carrier` field (it's
computed and used transiently in `createShipping` and carried in
`ShippingCreateRequest`/`ShippingWebhookPayload`, but never saved), and there is no
field to remember Sapo's own Fulfillment `id` for later `complete`/`cancel` calls.

## Goals

- Push a Sapo Fulfillment when `createShipping` assigns a tracking number to an
  already-Sapo-synced order.
- Reflect GHN's delivered/returned status transitions in Sapo via
  `complete.json`/`cancel.json`, driven by the existing `handleWebhook` handler.
- Reflect `cancelShipping` (manual admin cancel) via `cancel.json`.
- Persist `carrier` durably on `Order`, and persist Sapo's `sapoFulfillmentId` so
  later lifecycle calls target the right Fulfillment.
- Follow the same async/fire-and-forget, retry-on-schedule pattern already
  established by `SapoOrderSyncService` and `SapoCustomerSyncService`.

## Non-goals

- **No inbound sync.** Sapo → FashionVista fulfillment updates are not handled by
  this spec (explicit decision — FashionVista's GHN webhook is the source of truth
  for shipment status; Sapo's copy is a mirror, not a second source).
- **No partial/multi-shipment support.** `Order` has a single `trackingNumber`
  today; this spec keeps the one-Order-to-at-most-one-Fulfillment model. Splitting
  an order into multiple shipments is a future, unscoped change.
- **No retry for `complete`/`cancel` failures.** Only failed *creates* are retried
  on a schedule (see Error Handling). A failed complete/cancel leaves the local
  `OrderStatus` unaffected (GHN remains authoritative) and is left for the existing
  Inventory/Order-domain reconciliation job to catch as drift, rather than building
  a second retry mechanism for this domain.
- **No changes to `ShippingCreateRequest`/`ShippingWebhookPayload` DTOs** — they
  already carry `carrier`; this spec only makes it durable.
- **No Sapo Carrier Service API integration** (a distinct, unrelated Sapo resource
  for providing live shipping rates) — out of scope, not needed for this work.

## Architecture

New components, following the pattern already established by
`SapoOrderSyncService`/`SapoCustomerSyncService`:

- **`SapoShippingSyncService`** (`integration/sapo/service/`) — new service with
  `pushFulfillment(Long orderId)` (`@Async("sapoShippingTaskExecutor")`),
  `completeFulfillment(Long orderId)`, `cancelFulfillment(Long orderId)`, and
  `retryFailedFulfillments()` (`@Scheduled`). Invoked from `ShippingServiceImpl`
  after each local state change commits.
- **`SapoApiClient`** gains three methods: `createFulfillment`,
  `completeFulfillment`, `cancelFulfillment`, mirroring the existing
  `createOrder`/`createProduct` shape (`RestClient` + Basic Auth, already
  configured).
- **New DTOs**: `SapoFulfillmentPushRequest` / `SapoFulfillmentPushResponse`
  (`integration/sapo/dto/`), built with Lombok `@Builder`, mirroring
  `SapoOrderPushRequest`/`Response`.
- **New async executor bean**: `sapoShippingTaskExecutor`, one dedicated executor
  per domain (matches `sapoOrderTaskExecutor`, `sapoCustomerTaskExecutor`).
- **`ShippingServiceImpl`** gains three call sites into `SapoShippingSyncService`
  (see Data Flow) — no change to its existing GHN logic or return types.

## Data Model Changes

New migration `V13__add_sapo_fulfillment_fields_to_orders.sql` (following the `V12`
precedent from the Customer domain), adding to `orders`:

| Column | Type | Purpose |
|---|---|---|
| `carrier` | `VARCHAR(50)`, nullable | Durable version of the value `createShipping` already computes locally |
| `sapo_fulfillment_id` | `VARCHAR(64)`, nullable | Sapo's Fulfillment id, set after first successful push; required for `complete`/`cancel` calls |
| `sapo_fulfillment_sync_status` | enum (reuses existing `SapoSyncStatus`), nullable | Fulfillment-level push status — kept separate from `sapo_sync_status` because that field tracks the **order** push, a distinct Sapo API call with independent success/failure |
| `sapo_fulfillment_sync_error` | `VARCHAR(500)`, nullable | Last fulfillment push error message |
| `sapo_fulfillment_synced_at` | `DATETIME`, nullable | Timestamp of last successful fulfillment sync |

`SapoSyncStatus` (`PENDING`/`SYNCED`/`FAILED`) is reused as-is — no new enum.

## Data Flow

Three trigger points in `ShippingServiceImpl`, each firing the sync call
asynchronously after the local transaction commits (fire-and-forget — a Sapo
failure never blocks or reverts the local shipping action):

**1. `createShipping`** (after `orderRepository.save(order)`):
- If `order.getSapoOrderId() == null` (order never synced to Sapo) → skip, log at
  WARN, do not schedule a retry. This is a one-time opportunity tied to the create
  action, not a standing condition worth polling for.
- Else → `sapoShippingSyncService.pushFulfillment(order.getId())`:
  `createFulfillment` with `tracking_number` = the generated tracking number,
  `tracking_company` = `carrier`, `notify_customer = false`. On success: persist
  `sapoFulfillmentId`, `sapoFulfillmentSyncStatus = SYNCED`,
  `sapoFulfillmentSyncedAt = now()`. On failure: `sapoFulfillmentSyncStatus = FAILED`
  + error message, logged at ERROR, not rethrown.

**2. `handleWebhook`** (after the existing GHN-status switch resolves a new
`OrderStatus`):

| GHN webhook status | Local `OrderStatus` | Sapo action |
|---|---|---|
| `pickedup`, `intransit` | `SHIPPING` | none — Sapo Fulfillment has no in-transit state; stays `pending` |
| `delivered` | `DELIVERED` | `completeFulfillment` → Sapo status becomes `success` |
| `return`, `returned` | `CANCELLED` | `cancelFulfillment` → Sapo status becomes `cancelled` |

If `sapoFulfillmentId` is null when `delivered`/`return`/`returned` fires (the
create-time push was skipped or failed), skip and log at WARN — same treatment as
the create-time skip.

**3. `cancelShipping`** (manual admin cancel, after clearing `trackingNumber` and
reverting `OrderStatus`): if `sapoFulfillmentId` is set, call `cancelFulfillment`,
then clear `sapoFulfillmentId` locally so a subsequent `createShipping` on the same
order pushes a fresh Fulfillment rather than reusing a cancelled one.

## Error Handling & Retry

- `pushFulfillment`, `completeFulfillment`, `cancelFulfillment` each wrap their
  `SapoApiClient` call in try/catch: on `RuntimeException`, set
  `sapoFulfillmentSyncStatus = FAILED` + `sapoFulfillmentSyncError`, log at ERROR,
  do not rethrow (matches `SapoOrderSyncService.doPush`).
- A failed `complete`/`cancel` call does **not** roll back the local `OrderStatus`
  change — GHN is authoritative for shipment status regardless of Sapo's mirror
  state, consistent with how `handleWebhook` already treats GHN today.
- **Scheduled retry covers create-failures only**:
  `retryFailedFulfillments()` (`@Scheduled(cron = "0 30 * * * ?")`, offset 30
  minutes from `SapoOrderSyncService`'s `:00` job to avoid contention) queries
  orders where `sapoFulfillmentSyncStatus = FAILED AND trackingNumber IS NOT NULL`
  and re-attempts `createFulfillment` only. Complete/cancel failures are not
  retried by this job (see Non-goals) — they're left for the existing
  reconciliation job from the Inventory/Order domain to surface as drift.

## Testing

- **`SapoShippingSyncServiceTest`** (new): mocked `SapoApiClient` +
  `OrderRepository`. Cases: successful push sets `sapoFulfillmentId`/`SYNCED`;
  client exception sets `FAILED` + error message; skip-when-`sapoOrderId`-null
  makes no client call; skip-when-`sapoFulfillmentId`-null on complete/cancel makes
  no client call; `retryFailedFulfillments` only picks up `FAILED` rows with a
  non-null `trackingNumber`.
- **`SapoApiClientTest`** additions: `createFulfillment`/`completeFulfillment`/
  `cancelFulfillment` hit the expected URIs and bodies, mirroring the existing
  `createOrder` test.
- **`ShippingServiceImplTest`** additions: `createShipping`/`handleWebhook`/
  `cancelShipping` each invoke `SapoShippingSyncService` (mocked) with the correct
  arguments at the correct trigger points; skip conditions are verified to not
  invoke it.
- Migration gets the existing Flyway-validation test coverage — no new test infra.

## Summary of New/Modified Files

- **New:** `integration/sapo/service/SapoShippingSyncService.java`
- **New:** `integration/sapo/dto/SapoFulfillmentPushRequest.java`,
  `SapoFulfillmentPushResponse.java`
- **New:** `db/migration/V13__add_sapo_fulfillment_fields_to_orders.sql`
- **Modify:** `integration/sapo/client/SapoApiClient.java` (add 3 methods)
- **Modify:** `entity/Order.java` (add 5 fields)
- **Modify:** `service/impl/ShippingServiceImpl.java` (3 new call sites)
- **Modify:** async executor config (add `sapoShippingTaskExecutor` bean)
- **Modify:** `SapoShippingSyncServiceTest.java`, `SapoApiClientTest.java`,
  `ShippingServiceImplTest.java` (new/extended test cases)
