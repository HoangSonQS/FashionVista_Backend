# Sapo Ledger Sync — Design Spec

**Date:** 2026-08-27
**Scope (this spec):** Outbound-only Sapo Transaction sync for the Ledger domain —
the seventh and final domain of the 7-domain Sapo integration decomposition
(Product, Order, Inventory, Voucher, Customer, Shipping done or in progress).

## Context

FashionVista's Sapo integration is being built out domain by domain (see
`docs/superpowers/specs/2026-08-06-sapo-sync-health-design.md` for the original
7-domain decomposition, which named Ledger only as a placeholder — "brand-new
domains... zero Sapo integration today"). This spec gives Ledger its first real
scope: pushing FashionVista's own `Payment` and `Refund` events to Sapo as
Transaction records on the corresponding Sapo order.

Sapo's real Transaction API (verified against
[support.sapo.vn/transaction](https://support.sapo.vn/transaction), not just this
repo's own inbound spec at `docs/sapo-api-reference.md`) is a sub-resource of Order:
`POST /admin/orders/{order_id}/transactions.json`, with fields `amount`, `kind`
(`authorization`|`sale`|`capture`|`void`|`refund`), `gateway`, `status`
(`pending`|`failure`|`success`|`error`), `currency`, `parent_id` (links e.g. a
refund back to its capture), among others. FashionVista has no separate
authorization/capture step — both online (VNPay) and manual (COD/admin-confirmed)
payments go directly to `PaymentStatus.PAID` — so the natural mapping is a single
combined `kind=sale` push on that transition, and a `kind=refund` push per
`Refund` row created by `AdminOrderServiceImpl.createPartialRefund()`.

## Goals

- Push a Sapo `sale` transaction when a `Payment` transitions to `PaymentStatus.PAID`,
  from either trigger site (`VnPayController` online-gateway callback,
  `AdminPaymentServiceImpl` admin/COD manual confirm).
- Push a Sapo `refund` transaction when `AdminOrderServiceImpl.createPartialRefund()`
  creates a new `Refund` row.
- Follow the same async/fire-and-forget, retry-on-schedule pattern already
  established by `SapoOrderSyncService` and `SapoShippingSyncService`.
- Follow the same `sapo<Domain>Id`/`SyncStatus`/`SyncError`/`SyncedAt` field
  naming convention already established on `Order`.

## Non-goals

- **No inbound sync.** Sapo webhook topics `order_transactions/create` and
  `refunds/create` are not handled by this spec — FashionVista's own Payment/Refund
  state is the source of truth; Sapo's copy is a mirror, not a second source.
- **No `authorization`/`capture`/`void` kinds.** FashionVista's payment flows never
  produce these states, so only `sale` and `refund` are ever pushed.
- **No push for failed/pending payment attempts.** Only a confirmed `PAID`
  transition triggers a push (`status=success` always) — we don't push
  `pending`/`failure`/`error` Sapo transaction states for abandoned or declined
  payment attempts.
- **No automatic refund-on-cancel.** `OrderServiceImpl.cancelMyOrder()` only
  restocks items and sets `OrderStatus.CANCELLED` — it does not create a `Refund`
  row today, so order cancellation triggers no Ledger push. If that changes in the
  future, it will flow through the same `createPartialRefund`-triggered hook,
  needing no new integration point.
- **No `parent_id` lineage tracking.** Sapo's `parent_id` field (linking a refund
  transaction back to its originating sale) is not populated in this design —
  each push is independent. Deferred; can be added later as one more column on
  `Refund` without restructuring.

## Architecture

New components, following the pattern already established by
`SapoOrderSyncService`/`SapoShippingSyncService`:

- **`SapoLedgerSyncService`** (`integration/sapo/service/`) — new service with
  `pushPaymentTransaction(Long paymentId)` and `pushRefundTransaction(Long refundId)`
  (both `@Async("sapoLedgerTaskExecutor")`), plus `retryFailedTransactions()`
  (`@Scheduled`). Invoked directly from `VnPayController`, `AdminPaymentServiceImpl`,
  and `AdminOrderServiceImpl` right after each respective local save (see Data Flow
  for the exact call-site convention).
- **`SapoApiClient`** gains one method: `createTransaction(String sapoOrderId,
  SapoTransactionRequest request)`, mirroring the existing
  `createFulfillment`/`createCustomer` shape (`RestClient` + Basic Auth, already
  configured).
- **New DTOs**: `SapoTransactionRequest` / `SapoTransactionResponse`
  (`integration/sapo/dto/`), built with Lombok `@Builder`, mirroring
  `SapoFulfillmentPushRequest`/`Response`.
- **New async executor bean**: `sapoLedgerTaskExecutor`, matching
  `sapoOrderTaskExecutor`/`sapoShippingTaskExecutor` shape exactly
  (`corePoolSize=2`, `maxPoolSize=5`, `queueCapacity=50`, prefix `"sapo-ledger-"`).
- **`VnPayController`**, **`AdminPaymentServiceImpl`**, **`AdminOrderServiceImpl`**
  each gain one call site into `SapoLedgerSyncService` (see Data Flow) — no change
  to their existing return types or business logic.

## Data Model Changes

New migration `V14__add_sapo_transaction_fields_to_payments_and_refunds.sql`
(next available version after Shipping's `V13`; ⚠️ the not-yet-merged Customer
branch also claims a migration number in this range — whichever of Customer/Ledger
merges to `main` second must renumber at merge time, per the existing tracked
collision risk), adding the same four columns to both `payments` and `refunds`:

| Column | Type | Purpose |
|---|---|---|
| `sapo_transaction_id` | `VARCHAR(64)`, nullable | Sapo's returned Transaction `id` |
| `sapo_sync_status` | enum (reuses existing `SapoSyncStatus`), nullable | `PENDING`/`SYNCED`/`FAILED` |
| `sapo_sync_error` | `VARCHAR(500)`, nullable | Last push error message |
| `sapo_synced_at` | `DATETIME`, nullable | Timestamp of last successful push |

`SapoSyncStatus` (`PENDING`/`SYNCED`/`FAILED`) is reused as-is — no new enum. Both
`Payment` and `Refund` get their own independent copy of these four fields (a
`Payment` row and its `Refund` rows are separate Sapo Transaction pushes with
independent success/failure).

## Data Flow

**1. Payment success → `kind=sale`.** Both existing `PaymentStatus.PAID`-transition
sites call `sapoLedgerSyncService.pushPaymentTransaction(payment.getId())` directly,
right after their own `paymentRepository.save(payment)`/`orderRepository.save(order)`
calls — the same direct-call convention `AdminOrderServiceImpl` already uses for
`sapoOrderSyncService.pushOrder(saved.getId())` (no `afterCommit` wrapper; the
target method is itself `@Async`, so it runs on a separate thread pool regardless).
This spec does not introduce `ShippingServiceImpl`'s `afterCommitOrNow` wrapper into
`VnPayController`/`AdminPaymentServiceImpl`/`AdminOrderServiceImpl` — none of the
three currently has it, and adding a new cross-cutting helper for this spec alone
would be scope creep beyond what Ledger needs.
- `VnPayController` — online-gateway callback success path (`processPaymentResult`,
  called from `handleReturn`/`handleIpn`, both already `@Transactional`).
- `AdminPaymentServiceImpl` — admin/COD manual confirm path.

Inside `pushPaymentTransaction`: re-fetch `Payment` by id (avoids passing a
detached entity across the async boundary). If `payment.getOrder().getSapoOrderId()
== null` (order never synced to Sapo), skip and log at WARN. This skip is terminal
until the next natural PAID-transition trigger — the scheduled retry job (below)
only re-drives rows already marked `FAILED`, not `PENDING`-and-skipped ones,
mirroring `SapoShippingSyncService`'s identical skip-is-terminal behavior.

Else: build `SapoTransactionRequest` (`amount` = `payment.getAmount()`, `kind =
"sale"`, `gateway` = mapped from `PaymentMethod`, `currency = "VND"`, `status =
"success"`) and call `sapoApiClient.createTransaction(sapoOrderId, request)`. On
success: `sapoTransactionId`, `sapoSyncStatus = SYNCED`, `sapoSyncedAt = now()`.
On `RuntimeException`: `sapoSyncStatus = FAILED` + error message, logged at ERROR,
not rethrown.

**Gateway string mapping** (`PaymentMethod` → Sapo `gateway` string):

| `PaymentMethod` | `gateway` |
|---|---|
| `COD` | `"Cash on Delivery"` |
| `BANK_TRANSFER` | `"Bank Transfer"` |
| `VNPAY` | `"VNPay"` |
| `MOMO` | `"MoMo"` |

**2. Refund creation → `kind=refund`.** `AdminOrderServiceImpl.createPartialRefund()`
already saves the new `Refund` row (`refundRepository.save(refund)`) before saving
the updated `Payment`/`Order`. Immediately after those saves, the method directly
calls `sapoLedgerSyncService.pushRefundTransaction(refund.getId())` — same
direct-call convention as the existing `pushOrder` call elsewhere in this class.
Same skip-on-no-`sapoOrderId` behavior. Request: `amount = refund.getAmount()`, `kind = "refund"`,
`gateway` mapped from `refund.getOrder().getPaymentMethod()` (the original payment
method, since `RefundMethod` describes how money physically moves back, not a
Sapo-recognized gateway), `currency = "VND"`, `status = "success"`.

## Error Handling & Retry

- Both push methods wrap their `SapoApiClient` call in try/catch: on
  `RuntimeException`, set `sapoSyncStatus = FAILED` + `sapoSyncError`, log at
  ERROR, do not rethrow (matches `SapoShippingSyncService.doPushFulfillment`). A
  failed Ledger push never rolls back or blocks the local Payment/Refund state —
  Sapo's ledger is a mirror, not a gate.
- **Scheduled retry**: `retryFailedTransactions()`
  (`@Scheduled(cron = "0 45 * * * ?")`, offset from Order's `:00` and Shipping's
  `:30` to avoid contention against Sapo) runs two queries —
  `paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(FAILED)` and
  `refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(FAILED)` — and
  re-invokes the corresponding push method for each row.
- The `sapoTransactionId IS NULL` filter (mirroring
  `SapoShippingSyncService`'s `sapoFulfillmentIdIsNull` retry guard) protects
  against double-pushing: if Sapo accepted a transaction but our own save of
  `SYNCED` failed afterward (rare network-partition case), the row would already
  carry a `sapoTransactionId` and the retry job leaves it alone rather than
  creating a duplicate Sapo transaction.
- No max-retry cap or exponential backoff — matches the existing Order/Shipping
  precedent, neither of which has one either.

## Testing

- **`SapoLedgerSyncServiceTest`** (new): mocked `SapoApiClient` + `PaymentRepository`
  + `RefundRepository`. Cases: successful payment push sets
  `sapoTransactionId`/`SYNCED`; successful refund push likewise; client exception
  sets `FAILED` + error message on either path; skip-when-`sapoOrderId`-null makes
  no client call for either path; `retryFailedTransactions` only re-drives `FAILED`
  rows with a null `sapoTransactionId`, for both Payment and Refund.
- **`SapoApiClientTest`** additions: `createTransaction` hits the expected URI and
  body, mirroring the existing `createFulfillment` test.
- **Gateway-mapping unit test**: all four `PaymentMethod` values map to the
  expected Sapo `gateway` string.
- **`VnPayControllerTest`**, **`AdminPaymentServiceImplTest`**,
  **`AdminOrderServiceImplTest`** additions: each PAID-transition/refund-creation
  path invokes `SapoLedgerSyncService` (mocked) with the correct id at the correct
  point; skip conditions verified to not invoke it.
- Migration gets the existing Flyway-validation test coverage — no new test infra.

## Summary of New/Modified Files

- **New:** `integration/sapo/service/SapoLedgerSyncService.java`
- **New:** `integration/sapo/dto/SapoTransactionRequest.java`,
  `SapoTransactionResponse.java`
- **New:** `db/migration/V14__add_sapo_transaction_fields_to_payments_and_refunds.sql`
- **Modify:** `integration/sapo/client/SapoApiClient.java` (add `createTransaction`)
- **Modify:** `entity/Payment.java`, `entity/Refund.java` (add 4 fields each)
- **Modify:** `repository/PaymentRepository.java`, `repository/RefundRepository.java`
  (add `findBySapoSyncStatusAndSapoTransactionIdIsNull`)
- **Modify:** `controller/VnPayController.java`,
  `service/impl/AdminPaymentServiceImpl.java`,
  `service/impl/AdminOrderServiceImpl.java` (one call site each)
- **Modify:** `config/AsyncConfig.java` (add `sapoLedgerTaskExecutor` bean)
- **New/Modify tests:** `SapoLedgerSyncServiceTest.java`, `SapoApiClientTest.java`,
  `VnPayControllerTest.java`, `AdminPaymentServiceImplTest.java`,
  `AdminOrderServiceImplTest.java`
