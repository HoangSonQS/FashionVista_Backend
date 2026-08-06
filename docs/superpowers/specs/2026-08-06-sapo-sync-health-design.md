# Sapo Sync Health & Remediation — Design Spec

**Date:** 2026-08-06
**Scope (this spec):** Generic reconciliation framework + Inventory domain + Order domain
(closing the existing retry-job gap). Product domain, a unified multi-domain admin
dashboard covering Product, and brand-new domains (Customer, Voucher, Shipping,
Ledger) are explicitly **out of scope** — future sub-projects built on top of this
framework once it proves out with two domains.

## Context

This is a sub-project of a larger request: build periodic monitoring across every
subsystem connected to Sapo, alert admins on drift, and provide manual DB↔Sapo
remediation. The full request spans Product, Order, Inventory, Customer, Voucher,
Shipping, and Ledger — too large for one spec. It was decomposed with the user into
independent sub-projects; this spec covers the first one: the reusable framework,
proven with Inventory (the most urgent, evidence-backed gap) and Order (closing a
real gap found during production testing).

**Evidence driving this work** (found via live production testing on 2026-08-06):

1. **Inventory desync, no outbound path exists.** 3 test orders reduced local DB
   stock to 17, but Sapo still showed 20. The only inventory integration that exists
   is an **inbound** webhook (`SapoWebhookController` handles Sapo → FV stock
   pushes). There is no FV → Sapo direction at all, so any stock change from an
   order, cancellation, or return never reaches Sapo.

2. **Order retry job permanently orphans certain orders.**
   `SapoOrderSyncService.retryFailedSyncs()` runs hourly but only retries orders
   where `sapoSyncStatus=FAILED AND status=CONFIRMED`. Two real orders were
   observed falling outside this filter forever:
   - Order 8: pushed once, still `sapoSyncStatus=PENDING` (never went through the
     failure path), then moved to `PROCESSING` — filter excludes it on both
     `sapoSyncStatus` and `status`.
   - Order 9: failed sync while `CONFIRMED` (`sapoSyncStatus=FAILED`), then moved
     to `SHIPPING` before the hourly job could catch it — filter excludes it on
     `status`.

   Root cause of order 9's failure itself (`source_name: "web"` being a Sapo
   protected value) was already found and fixed separately (commit `aa916c8`,
   per Sapo's official docs at
   [support.sapo.vn/cac-thuoc-tinh-cua-order-api](https://support.sapo.vn/cac-thuoc-tinh-cua-order-api)).
   This spec addresses the *detection/alerting* gap, not that root cause.

## Goals

- A generic, pluggable reconciliation framework: adding a new domain later
  (Customer, Voucher, Shipping, Ledger, or a fuller Product check) means writing
  one class, not touching the scheduler, email, or admin plumbing.
- Every 30 minutes, check DB↔Sapo consistency for Inventory and Order.
- On a **newly detected** discrepancy, send one alert email to a configured admin
  address. Never re-send for a discrepancy still open from a prior cycle.
- An admin page listing open discrepancies with manual remediation actions:
  push DB→Sapo, pull Sapo→DB (where meaningful), or mark resolved.
- Close the real gap: inventory changes from orders/cancellations/returns get
  pushed to Sapo in near-real-time, with the 30-minute job as a safety net for
  anything the real-time push misses.
- Close the real gap: orders that fall outside the existing hourly retry job's
  filter are still detected and surfaced (not silently orphaned), even though
  this spec does not change the existing hourly job itself.

## Non-goals

- Automatic remediation. The 30-minute job only detects and alerts; all fixes are
  admin-triggered from the admin page. (Explicit user decision — auto-fixing risks
  pushing the wrong direction in edge cases like a return being processed
  concurrently.)
- Modifying or removing the existing `SapoOrderSyncService.retryFailedSyncs()`
  hourly job. It keeps running as-is; this framework adds detection/alerting on
  top, so nothing it currently retries is affected.
- Building outbound sync for Customer, Voucher, Shipping, or Ledger — those
  domains have zero Sapo integration today; there is nothing yet to reconcile.
  Future sub-projects.
- A unified dashboard covering Product (which already has `/api/admin/sapo/products/{id}/retry-sync`
  and `/migrate` but no UI). Product gets its own admin UI in a later sub-project;
  this spec's admin page only covers Inventory and Order.
- Rate-limit-aware batching/backoff for the Sapo read calls in the periodic check.
  Current catalog size is small; revisit if it becomes a problem.

## Architecture

```
integration/sapo/synchealth/
  SapoSyncHealthCheck.java        # interface: List<SyncDiscrepancy> checkAll()
  SyncHealthScheduler.java        # @Scheduled(fixedDelay = 30 min)
  InventorySyncHealthCheck.java   # implements SapoSyncHealthCheck
  OrderSyncHealthCheck.java       # implements SapoSyncHealthCheck
  SyncDiscrepancyService.java     # dedup, persistence, triggers email on new discrepancies
  SapoInventorySyncService.java   # NEW: real-time push of stock changes to Sapo
```

`SyncHealthScheduler` depends on `List<SapoSyncHealthCheck>` — Spring injects every
bean implementing the interface. Adding a domain later means adding one
`@Component implements SapoSyncHealthCheck` class; no existing file changes.

Each check's `checkAll()` catches its own exceptions internally (e.g. Sapo API
timeout) and returns whatever partial results it has, logging the failure. One
domain's Sapo outage never prevents other domains from being checked in the same
cycle.

## Data Model

New table `sync_discrepancy`:

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `domain` | VARCHAR (enum `SyncDomain`: `INVENTORY`, `ORDER`) | extend enum for future domains |
| `entity_id` | BIGINT | variant id or order id |
| `entity_label` | VARCHAR | SKU or order number, for admin readability |
| `discrepancy_type` | VARCHAR (enum `DiscrepancyType`: `NOT_SYNCED`, `VALUE_MISMATCH`, `SYNC_FAILED`) | |
| `details` | TEXT | human-readable, e.g. `"DB stock=17, Sapo stock=20"` |
| `detected_at` | TIMESTAMP | set once, on first detection |
| `last_seen_at` | TIMESTAMP | updated every cycle the discrepancy is still open |
| `resolved_at` | TIMESTAMP, nullable | null = open |
| `alert_sent_at` | TIMESTAMP, nullable | set when the alert email fires; prevents re-sending |

Dedup key: `(domain, entity_id, discrepancy_type)` where `resolved_at IS NULL`. Each
cycle, `SyncDiscrepancyService` upserts on this key — an existing open row gets
`last_seen_at` bumped (no new email); a genuinely new one gets inserted and queued
for the alert email.

## Inventory Domain

**Real-time push (`SapoInventorySyncService.pushStock(Long variantId)`):**
Hooked into every code path that mutates `ProductVariant.stock` as a side effect of
an order (confirm, cancel, return). Reuses the existing, already-proven
`SapoApiClient.updateProduct()` (the `SapoProductPushRequest` variant payload
already carries `inventory_quantity`) — no new Sapo endpoint needed. Skips variants
where `sapoVariantId == null` (never synced yet; will get the right stock value
whenever the product is first pushed). Failures are logged, not thrown — never
block the customer-facing order/cancel/return flow. The 30-minute check below is
the safety net for anything a failed push misses.

**Periodic check (`InventorySyncHealthCheck`):** For every `ProductVariant` with
`sapoVariantId != null`, call `GET /admin/products/{sapoProductId}/variants.json`
(per [support.sapo.vn/product-variant](https://support.sapo.vn/product-variant) —
returns `inventory_quantity` per variant) and compare to local `stock`. Mismatch →
upsert a `VALUE_MISMATCH` discrepancy.

**Manual remediation actions:**
- Push to Sapo: call `pushStock(variantId)` immediately, resolve on success.
- Pull from Sapo: overwrite local `stock` with the value just read from Sapo,
  resolve on success.

## Order Domain

**`OrderSyncHealthCheck`** (additive — does not modify `retryFailedSyncs()`):
flags any order where `status IN (CONFIRMED, PROCESSING, SHIPPING, DELIVERED)` (i.e.
it was already eligible to be pushed) **and** `sapoSyncStatus != SYNCED`:
- `sapoSyncStatus == PENDING` → `NOT_SYNCED` discrepancy (never attempted, e.g. the
  order-8 case)
- `sapoSyncStatus == FAILED` → `SYNC_FAILED` discrepancy (e.g. the order-9 case,
  including ones that later moved past `CONFIRMED`)

This closes the detection gap: nothing the hourly job's narrower filter excludes
stays invisible anymore, even though the hourly job's own retry behavior is
unchanged.

**Manual remediation actions:**
- Push to Sapo: call `SapoOrderSyncService.pushOrder(orderId)` immediately (same
  push used by the confirm-order flow and the hourly retry), resolve on success.
- Link Sapo order (special case): admin supplies an existing `sapoOrderId` (e.g.
  they manually created the order in Sapo's POS to work around the outage) — sets
  the local order's `sapoOrderId`, marks `sapoSyncStatus=SYNCED`, resolves the
  discrepancy. Rejects if that `sapoOrderId` is already linked to a different local
  order.

## Email Alerting

- New config `admin.alert.email` in `application.properties` (not a secret —
  belongs alongside other non-secret config, unlike `.env`).
- `EmailService.sendSyncDiscrepancyAlert(List<SyncDiscrepancy> newlyDetected)` — one
  email per scheduler cycle that found new discrepancies (not one email per
  discrepancy), listing domain, entity, and details for each.
- Never re-sent for a discrepancy still open in a later cycle (enforced via
  `alert_sent_at` / the dedup key in `SyncDiscrepancyService`).

## Admin API

`AdminSyncHealthController`, base path `/api/admin/sapo/sync-health`,
`@PreAuthorize("hasRole('ADMIN')")` (same pattern as the existing
`AdminSapoSyncController`):

- `GET /discrepancies?domain=&status=OPEN|RESOLVED` — paginated list
- `POST /discrepancies/{id}/push-to-sapo` — DB→Sapo (Inventory: push stock; Order:
  push order now)
- `POST /discrepancies/{id}/pull-from-sapo` — Sapo→DB (Inventory only)
- `POST /discrepancies/{id}/link-sapo-order` — body `{ sapoOrderId }` (Order only,
  special-case manual linking)
- `POST /discrepancies/{id}/resolve` — mark resolved with no automated action
  (admin judged it a non-issue)
- `POST /run-now` — trigger a check cycle immediately, outside the 30-minute
  schedule (useful for admins verifying a fix worked)

Actions that don't apply to a given discrepancy's domain (e.g. `pull-from-sapo` on
an `ORDER` discrepancy) return `400 Bad Request`.

## Admin UI

New page `AdminSyncHealth.tsx` (FashionVista_Admin), route `/sync-health`, added to
the sidebar nav in `AdminLayout`. Table of open discrepancies: Domain | Entity |
Discrepancy type | Details | Detected at | Actions. Actions rendered per-row based
on `domain` (Inventory rows get Push/Pull buttons; Order rows get Push/Link-Sapo-Order);
every row also gets a Resolve button. This page is scoped to Inventory and Order
only for this spec — Product and future domains get their own UI in later
sub-projects, though the same discrepancy table and API already support them as
soon as a `SapoSyncHealthCheck` implementation exists for them.

## Error Handling

- A domain check throwing (e.g. Sapo API timeout) is caught inside that check's
  `checkAll()`; the scheduler still runs every other registered check that cycle.
- Real-time inventory push failures never propagate to the customer-facing
  order/cancel/return request — logged only, caught by the next periodic check.
- Manual remediation actions (`push-to-sapo`, `pull-from-sapo`, `link-sapo-order`)
  run in a transaction and only mark `resolved_at` after the remote call succeeds.

## Testing

- Unit tests per `SapoSyncHealthCheck` implementation, mocking `SapoApiClient`
  (mismatch detected, no-mismatch case, Sapo-call-throws case).
- `SyncDiscrepancyService` dedup tests: repeated detection of the same
  `(domain, entity_id, discrepancy_type)` updates `last_seen_at` without a new row
  or a second email; a genuinely new discrepancy does trigger one.
- `SyncHealthScheduler` test verifying it invokes every registered check and
  survives one check throwing.
- Controller tests for each admin endpoint, including the `400` case for a
  domain/action mismatch, following the existing Mockito pattern in
  `AdminOrderServiceImplTest`.
- `SapoInventorySyncService` push tests: success path sets no discrepancy state
  (it's not part of the discrepancy table itself), failure path logs and does not
  throw to the caller.
