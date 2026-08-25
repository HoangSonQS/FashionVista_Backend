# Sapo Voucher Outbound Sync & Sync-Health — Design Spec

**Date:** 2026-08-20
**Scope (this spec):** Outbound push (Create/Update/Deactivate) for vouchers created
in FashionVista Admin, plus extending the existing Sync Health framework
([`2026-08-06-sapo-sync-health-design.md`](2026-08-06-sapo-sync-health-design.md))
with a `VOUCHER` domain. Customer, Shipping, and Ledger are explicitly **out of
scope** — separate future sub-projects.

## Context

Sub-project of the larger "Sapo integration" effort, decomposed per
[[sapo-domains-pending]]. Inventory and Order sync-health shipped and were
manually verified stable on production (2026-08-20). This is the next domain.

**Current state, found by reading the code (not assumed):**

- `SapoVoucherController` (`/api/sapo/v1/vouchers/*`) already lets **Sapo push
  vouchers INTO FashionVista** (create, get, list, mark-used) — this direction is
  done and out of scope here.
- `AdminVoucherServiceImpl` lets FashionVista admins create/update/delete vouchers
  **locally only** — nothing pushes these to Sapo. A voucher created in FashionVista
  Admin is invisible to Sapo POS/omnichannel.
- `Voucher` entity has no Sapo-linkage columns at all (unlike `ProductVariant`,
  which has `sapoVariantId` + `sapoSyncStatus`).
- No `VOUCHER` value exists in `SyncDomain`/no check implements `SapoSyncHealthCheck`
  for vouchers.

**Sapo API confirmed** (per Sapo Integration Rule — verified against
[support.sapo.vn/price-rule](https://support.sapo.vn/price-rule) and
[support.sapo.vn/discountcode](https://support.sapo.vn/discountcode), not assumed
from `docs/sapo-api-reference.md`, which is FashionVista's own inbound spec):

- Discounts are modeled as two nested resources: `PriceRule` (the discount logic —
  type, value, dates, usage limit, targeting) and `DiscountCode` (child of a
  `PriceRule`, the redeemable string, has a read-only `usage_count`).
- Full outbound CRUD is supported: `POST/PUT/GET/DELETE /admin/price_rules.json`
  and `POST/PUT/GET/DELETE /admin/price_rules/{id}/discount_codes.json`.
- `usage_count` on `DiscountCode` is documented as a **read-only** field on GET
  responses — no endpoint to set/increment it directly. This spec therefore does
  **not** attempt to push local `usedCount` to Sapo; usage reconciliation is
  explicitly out of scope (see Non-goals).

FashionVista's `Voucher` is a flattened single entity (one code, one rule) — maps
1:1 to one `PriceRule` + one `DiscountCode`, never many-codes-per-rule.

## Goals

- A voucher created or edited in FashionVista Admin is pushed to Sapo as a
  `PriceRule` + `DiscountCode` pair, so it's usable at Sapo POS/omnichannel.
- A voucher deleted in FashionVista Admin is deactivated (not hard-deleted) on the
  Sapo side, so in-store staff stop seeing it as valid without breaking Sapo-side
  history for any order that already redeemed it.
- Add `VOUCHER` to the existing Sync Health framework (`SyncDomain`,
  `SapoSyncHealthCheck`) so drift between local and Sapo voucher state is detected
  and surfaced on the same admin dashboard used for Inventory/Order, with the same
  four remediation actions where applicable.
- Push happens **after the enclosing transaction commits**, using the same
  `TransactionSynchronizationManager.afterCommit()` pattern already fixed for Order
  pushes this session (see [[sapo-order-push-race-condition]]) — not a new pattern,
  reused deliberately to avoid re-introducing that exact race condition.

## Non-goals

- **No backfill of pre-existing vouchers.** Only vouchers created/edited after this
  ships get pushed. Vouchers that predate this feature will show as `NOT_SYNCED` on
  the sync-health dashboard; admins push them manually one at a time via the
  existing "Push to Sapo" action if/when needed. (Explicit user decision — avoids a
  bulk-creation burst against Sapo's API with no clear urgency.)
- **No usage-count sync.** Sapo's `DiscountCode.usage_count` is read-only via API
  (confirmed above); this spec does not attempt to reconcile it. FashionVista
  remains the source of truth for `usedCount` regardless of what Sapo POS shows
  locally — unchanged from today.
- **No hard delete on Sapo.** Deleting a voucher in FashionVista Admin never calls
  `DELETE` on Sapo's `price_rules`/`discount_codes` — only deactivates (see Sync
  Flow below). Avoids the documented risk of Sapo rejecting deletion of a
  `price_rule` that already has redeemed codes.
- Customer, Shipping, Ledger domains — separate future sub-projects per
  [[sapo-domains-pending]].
- Any change to the existing inbound `SapoVoucherController` (Sapo → FashionVista
  direction) — it is already correct and untouched by this spec.

## Architecture

```
integration/sapo/service/
  SapoVoucherSyncService.java      # NEW: push/update/deactivate a Voucher on Sapo
integration/sapo/synchealth/
  VoucherSyncHealthCheck.java      # NEW: implements SapoSyncHealthCheck
entity/
  Voucher.java                     # + sapoPriceRuleId, sapoDiscountCodeId, sapoSyncStatus
  SyncDomain.java                  # + VOUCHER
service/impl/
  AdminVoucherServiceImpl.java     # create/update/delete schedule afterCommit push
integration/sapo/client/
  SapoApiClient.java                # + createPriceRule/updatePriceRule/getPriceRule,
                                     #   createDiscountCode/updateDiscountCode
```

`VoucherSyncHealthCheck` registers into the existing `List<SapoSyncHealthCheck>`
injection point in `SyncHealthScheduler` — no scheduler changes needed, per the
pluggable design already in place.

**Authentication & config — no new credentials needed.** The new
`createPriceRule`/`updatePriceRule`/`getPriceRule`/`createDiscountCode`/
`updateDiscountCode` methods are added to the existing `SapoApiClient`, so they
inherit its already-configured `RestClient` as-is:

- `Authorization: Basic base64(SAPO_OUTBOUND_API_KEY:SAPO_OUTBOUND_API_SECRET)`,
  bound via `SapoOutboundProperties` (`sapo.outbound.api-key` /
  `sapo.outbound.api-secret`) — the same credentials Order/Product push already
  use, supplied by Sapo when the store's outbound API access was provisioned.
- Base URL `https://${SAPO_STORE_DOMAIN}` (`sapo.outbound.store-domain`).
- `SAPO_WEBHOOK_SECRET` (`sapo.outbound.webhook-secret`) is unrelated to this
  spec — it verifies inbound Sapo webhook signatures and is not touched by this
  outbound-push feature.

This sub-project introduces zero new environment variables.

## Data Model

`Voucher` gains:

| Column | Type | Notes |
|---|---|---|
| `sapo_price_rule_id` | BIGINT, nullable | null = never pushed |
| `sapo_discount_code_id` | BIGINT, nullable | null = never pushed |
| `sapo_sync_status` | VARCHAR (existing `SapoSyncStatus` enum: `PENDING`, `SYNCED`, `FAILED`) | default `PENDING` on creation |

`SyncDomain` gains `VOUCHER`. No new discrepancy-table columns — `entity_id` stores
`Voucher.id`, `entity_label` stores `Voucher.code`.

## Sync Flow

**On create** (`AdminVoucherServiceImpl.createVoucher()`): after the voucher row
commits, `schedulePushVoucherAfterCommit(voucherId)` fires
`SapoVoucherSyncService.pushVoucher(voucherId)`:
1. `POST /admin/price_rules.json` — maps `type`/`value` to `value_type`/`value`
   (PERCENT→`percentage`, FIXED_AMOUNT→`fixed_amount`, FREESHIP→`target_type:
   shipping_line`), `usageLimit`→`usage_limit`, `startsAt`/`expiresAt`→
   `starts_on`/`ends_on`.
2. `POST /admin/price_rules/{id}/discount_codes.json` with `code`.
3. On success: store both returned ids, set `sapoSyncStatus = SYNCED`.
4. On failure (either call): set `sapoSyncStatus = FAILED`, log. Voucher row itself
   is never rolled back — local voucher stays usable on the website regardless of
   Sapo push outcome (same failure-isolation principle as Order push).

**On update** (`updateVoucher()`): after commit, if `sapoPriceRuleId != null`,
`PUT` both resources with changed fields — success keeps/sets `sapoSyncStatus =
SYNCED`, failure sets `FAILED` (same success/failure handling as create, step 3-4
above); if `sapoPriceRuleId == null` (never synced, or previously `FAILED`),
treated as a fresh create (falls into the same path as above).

**On delete** (`deleteVoucher()`): after commit, if `sapoPriceRuleId != null`,
`PUT /admin/price_rules/{id}.json` setting `ends_on` to now (deactivate, not
delete) — best-effort, logged on failure, never blocks the local delete which has
already committed.

## Sync-Health Domain Check

`VoucherSyncHealthCheck.checkAll()`, for every `Voucher` where `active = true`:

- `sapoSyncStatus == PENDING` and `createdAt` older than 10 minutes →
  `NOT_SYNCED` (push never fired or was silently lost — mirrors the Order
  `PENDING`→`NOT_SYNCED` rule; the 10-minute grace window avoids false positives on
  vouchers whose `afterCommit` push simply hasn't run yet)
- `sapoSyncStatus == FAILED` → `SYNC_FAILED`
- `sapoSyncStatus == SYNCED` → `GET /admin/price_rules/{id}.json`, compare
  `value` against Sapo's `value`, and `expiresAt` against Sapo's `ends_on`;
  mismatch on either → `VALUE_MISMATCH` with a human-readable diff in `details`
  (local `active` is not compared here — deactivation only happens via delete,
  which removes the local row entirely, so a `SYNCED` row is by definition still
  locally active)

Exceptions from the Sapo call are caught per-voucher inside `checkAll()` (one
Sapo timeout doesn't stop the rest of the batch), consistent with
`InventorySyncHealthCheck`.

## Admin API

Extends the existing `AdminSyncHealthController` (`/api/admin/sapo/sync-health`) —
no new controller. `domain=VOUCHER` becomes a valid filter value on
`GET /discrepancies`.

- `POST /discrepancies/{id}/push-to-sapo` — calls `pushVoucher`/update path,
  resolves on success. Valid for `VOUCHER`.
- `POST /discrepancies/{id}/pull-from-sapo` — overwrites local `value`/
  `expiresAt` with the value just read from Sapo, resolves on success. Valid for
  `VOUCHER`.
- `link-sapo-order` — **not applicable to `VOUCHER`**, returns `400` (existing
  domain-mismatch behavior, unchanged).
- `resolve` — unchanged, applies to any domain.
- `run-now` — unchanged, now also invokes `VoucherSyncHealthCheck`.

## Admin UI

No new page. `AdminSyncHealth.tsx`'s domain filter dropdown gains a `Voucher`
option; the existing per-row action rendering (already keyed off `domain`) gets a
`VOUCHER` case rendering Push + Pull + Resolve (same shape as Inventory rows —
Push/Pull/Resolve, no Link action).

## Error Handling

- Outbound push/update/deactivate never throws back into the admin request —
  scheduled `afterCommit`, so by definition it cannot affect the HTTP response for
  the create/update/delete call that triggered it.
- `VoucherSyncHealthCheck` isolates failures per-voucher; one bad Sapo response
  doesn't stop the rest of the check or other domains' checks that cycle.
- Manual remediation actions run in a transaction, mark `resolved_at` only after
  the remote Sapo call succeeds — unchanged pattern from Inventory/Order.

## Testing

- `SapoVoucherSyncServiceTest` — mock `SapoApiClient`: successful create (both ids
  stored, `SYNCED`), price-rule-succeeds-but-discount-code-fails (partial failure →
  `FAILED`, no orphaned id stored inconsistently), update path, deactivate-on-delete
  path.
- `VoucherSyncHealthCheckTest` — mirrors `InventorySyncHealthCheckTest`: `PENDING`
  within grace window → no discrepancy; `PENDING` past grace window → `NOT_SYNCED`;
  `FAILED` → `SYNC_FAILED`; `SYNCED` with matching/mismatching remote state →
  no discrepancy / `VALUE_MISMATCH`; Sapo-call-throws → caught, logged, no crash.
- `AdminVoucherServiceImplTest` — transaction-timing test asserting
  `pushVoucher()` is NOT invoked until after commit (same shape as the test written
  for `AdminOrderServiceImplTest` after the race-condition fix), so this bug class
  cannot regress here either.
- Controller tests for the new `domain=VOUCHER` filter and the `400` on
  `link-sapo-order` for a `VOUCHER` discrepancy, following the existing Mockito
  pattern in `AdminSyncHealthControllerTest`.
