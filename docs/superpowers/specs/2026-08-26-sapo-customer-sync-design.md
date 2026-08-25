# Sapo Customer Outbound Sync — Design Spec

**Date:** 2026-08-26
**Scope (this spec):** Outbound push (Create/Update) of FashionVista `User`
profile data to Sapo as a Customer record, triggered on registration and on
every profile update. Shipping and Ledger remain out of scope — separate
future sub-projects.

## Context

Sub-project of the larger "Sapo integration" effort, decomposed per
[[sapo-domain-roadmap]]. Inventory + Order shipped to production. Voucher
outbound push/deactivate is implementation-complete, merge pending. Customer
is the next domain.

**Current state, found by reading the code (not assumed):**

- The only existing customer-shaped data sent to Sapo is a nested `Customer`
  object (`email`, `phone` only) attached to `SapoOrderPushRequest` when an
  order is pushed. There is no standalone customer push — a user who
  registers but never orders is invisible to Sapo, and no profile field
  beyond email/phone ever reaches Sapo even after an order.
- `User` entity has no Sapo-linkage columns (unlike `ProductVariant` or the
  now-in-progress `Voucher`, which carry `sapoXId` + `sapoSyncStatus`).
- `SapoSyncStatus` (`PENDING`, `SYNCED`, `FAILED`) already exists as a
  generic, non-Voucher-scoped enum — reused here as-is.
- `AuthServiceImpl.register()` is `@Transactional`; `UserController.
  updateProfile()` has no `@Transactional` annotation and saves directly via
  `userRepository.save()`, which commits per-call. This asymmetry is load-
  bearing for the Sync Flow section below.
- `User.fullName` is a single string; Sapo requires separate `first_name`/
  `last_name`. `User.Gender` (`MALE`/`FEMALE`/`OTHER`) does not match Sapo's
  case-sensitive `"Male"/"Female"/"Other"` strings. `User.dateOfBirth` is a
  `LocalDate` whose default `toString()` already produces `yyyy-MM-dd`,
  matching Sapo's `dob` format with no conversion needed.

**Sapo API confirmed** (per Sapo Integration Rule — verified against Sapo's
real Admin API docs at support.sapo.vn, not assumed from
`docs/sapo-api-reference.md`, which is FashionVista's own inbound spec):

- Customers are a standalone resource: `POST /admin/customers.json` (create),
  `PUT /admin/customers/{id}.json` (update). Fields used here: `first_name`,
  `last_name`, `email`, `phone`, `gender`, `dob`, `tags` (a single
  comma-free string, not an array).

## Goals

- A user is pushed to Sapo as a Customer record — full profile (name split
  into first/last, gender, date of birth, tier as a tag) plus a persistent
  `sapoCustomerId` link — on registration and on every profile update.
- Only `role == CUSTOMER` users are ever pushed; ADMIN/STAFF accounts never
  get a `sapoCustomerId` and never trigger a push.
- Push happens after the enclosing transaction commits when one is active
  (`AuthServiceImpl.register()`), and directly when none is active
  (`UserController.updateProfile()`) — same `TransactionSynchronizationManager`
  pattern already established for Voucher, applied only where a transaction
  actually exists.
- Sync failures never surface to the end user and never block registration
  or profile update — `pushCustomer` is fire-and-forget `@Async`.

## Non-goals

- **No periodic reconciliation / sync-health check.** Push-only, no
  `CustomerSyncHealthCheck`, no `CUSTOMER` value added to `SyncDomain`. If a
  push silently fails and is never retried, the row stays `FAILED` with no
  automatic remediation — accepted for this sub-project; can be revisited
  later using the same framework Voucher/Inventory/Order already use.
- **No address sync.** `Address`/`CustomerAddress` data is not sent to Sapo.
  FashionVista's `Address` entity is itself stale after Vietnam's 2025
  province merger (see [[address-province-merger]]) — a separate, unrelated
  future sub-project, not folded into this scope.
- **No admin ban/status-change integration.** `AdminUserServiceImpl.
  updateUserStatus()` (which can set `AccountStatus.INACTIVE`, i.e. ban a
  user) is a separate code path from the approved trigger set (register +
  self-service profile update) and is not covered. A banned FashionVista
  user's Sapo customer record stays `state: enabled` regardless. Can be
  revisited later as its own scoped change.
- **Known limitation, accepted as-is: tags overwrite.** Sapo's `tags` field
  is a single string per customer, fully replaced on every push. Any tag a
  Sapo staff member manually adds to that customer record is silently lost
  on the next FashionVista-triggered push. Documented here, not solved —
  same category of accepted tradeoff as Voucher's deactivate-not-delete
  decision.
- Shipping, Ledger domains — separate future sub-projects per
  [[sapo-domain-roadmap]].
- Any change to the existing inbound customer fields on `SapoOrderPushRequest`
  — untouched by this spec.

## Architecture

```
integration/sapo/service/
  SapoCustomerSyncService.java     # NEW: push/update a User as a Sapo Customer
integration/sapo/util/
  SapoNameSplitter.java            # NEW: fullName -> {first, last}
integration/sapo/dto/
  SapoCustomerRequest.java         # NEW
  SapoCustomerResponse.java        # NEW
integration/sapo/client/
  SapoApiClient.java                # + createCustomer/updateCustomer
entity/
  User.java                        # + sapoCustomerId, sapoSyncStatus
config/
  AsyncConfig.java                 # + sapoCustomerTaskExecutor bean
service/impl/
  AuthServiceImpl.java             # register() schedules push after commit
controller/
  UserController.java              # updateProfile() calls push directly
```

**Authentication & config — no new credentials needed.** `createCustomer`/
`updateCustomer` are added to the existing `SapoApiClient`, inheriting its
already-configured `RestClient`:

- `Authorization: Basic base64(SAPO_OUTBOUND_API_KEY:SAPO_OUTBOUND_API_SECRET)`
  via `SapoOutboundProperties` — the same credentials Order/Product/Voucher
  push already use.
- Base URL `https://${SAPO_STORE_DOMAIN}` (`sapo.outbound.store-domain`).

This sub-project introduces zero new environment variables.

**New executor bean** — `AsyncConfig` gets a fourth dedicated
`ThreadPoolTaskExecutor`, same shape as the other three (core 2, max 5,
queue 50), named `sapoCustomerTaskExecutor` with thread prefix
`sapo-customer-`. The codebase's established convention is one executor per
Sapo domain, not a shared pool — Customer follows that convention rather
than reusing Voucher's.

## Approaches Considered

**1. Direct service call, mirroring the Voucher pattern (chosen).** A
dedicated `SapoCustomerSyncService.pushCustomer(Long userId)`, called
directly from `AuthServiceImpl.register()` (deferred to after-commit) and
`UserController.updateProfile()` (direct, no transaction active). Same
shape as the already-shipped Voucher and Order sync services — no new
architectural pattern introduced, smallest cognitive diff for whoever reads
this code next to Voucher's.

**2. Event-driven (`ApplicationEvent` + `@TransactionalEventListener`).**
Publish a `CustomerChangedEvent` from both trigger points; `
SapoCustomerSyncService` listens via `@TransactionalEventListener(phase =
AFTER_COMMIT)`, which handles the transaction-timing problem automatically
without hand-written `TransactionSynchronizationManager` code. Rejected:
introduces a pattern used nowhere else in the Sapo integration for the sake
of avoiding ~10 lines of boilerplate at only two call sites — the
inconsistency costs more than the boilerplate it removes.

**3. Extend the existing inline `Customer` object on `SapoOrderPushRequest`
only.** Add the extra profile fields there and rely on order pushes to carry
full customer data. Rejected: doesn't meet the approved goal — a customer
who registers or edits their profile but never places an order would never
sync, and a profile update after an existing order wouldn't re-sync either.

## Data Model

`User` gains:

| Column | Type | Notes |
|---|---|---|
| `sapo_customer_id` | BIGINT, nullable | null = never pushed |
| `sapo_sync_status` | VARCHAR (existing `SapoSyncStatus` enum: `PENDING`, `SYNCED`, `FAILED`) | default `PENDING` on creation; only meaningful for `role == CUSTOMER` |

No changes to `SyncDomain` (no sync-health check for this domain, see
Non-goals).

## Field Mapping

| FashionVista (`User`) | Sapo Customer field | Mapping |
|---|---|---|
| `fullName` | `first_name` / `last_name` | `SapoNameSplitter.splitLastName(fullName)` — split on the last space; trailing token → `last_name`, everything before → `first_name`; no space → `first_name` = whole string, `last_name` = empty |
| `email` | `email` | direct |
| `phoneNumber` | `phone` | direct |
| `gender` (`MALE`/`FEMALE`/`OTHER`) | `gender` | mapped to `"Male"/"Female"/"Other"` — not `.name()`, Sapo's values are case-sensitive |
| `dateOfBirth` (`LocalDate`) | `dob` | `dateOfBirth.toString()` — already `yyyy-MM-dd`, matches Sapo's required format with no custom parsing |
| `tier` (`CustomerTier`: BRONZE/SILVER/GOLD/PLATINUM) | `tags` | one fixed tag per tier, e.g. `fashionvista_tier_gold` — replaces the whole `tags` string (see Non-goals: tags overwrite) |
| `role` | — (filter, not mapped) | only `role == CUSTOMER` rows are pushed |

## Sync Flow

**On registration** (`AuthServiceImpl.register()`, `@Transactional`): after
`userRepository.save(user)`, `scheduleCustomerPushAfterCommit(user.getId())`
— a private helper structurally identical to `AdminVoucherServiceImpl`'s:
checks `TransactionSynchronizationManager.isSynchronizationActive()`,
registers an `afterCommit()` callback if so, else calls directly. Registers
the callback before the verification email is sent, but only fires after
the surrounding transaction commits, so the async task never reads
uncommitted user data.

**On profile update** (`UserController.updateProfile()`, no
`@Transactional`): after `userRepository.save(user)`, a **direct** call to
`sapoCustomerSyncService.pushCustomer(user.getId())` — no transaction is
active at this point (Spring Data's own per-call transaction already
committed inside `save()`), so no defer wrapper is needed.

**`SapoCustomerSyncService.pushCustomer(Long userId)`**
(`@Async("sapoCustomerTaskExecutor") @Transactional`):
1. Load the `User`. If not found, or `role != CUSTOMER`, log a warn and
   return — this is where the role filter is enforced, once, at the entry
   point.
2. `doPush(user)`: build the request via the Field Mapping table above;
   `POST /admin/customers.json` if `sapoCustomerId == null`, else
   `PUT /admin/customers/{id}.json`.
3. On success: store the returned Sapo customer id, set `sapoSyncStatus =
   SYNCED`.
4. On failure (null/incomplete response, or any `RuntimeException`): set
   `sapoSyncStatus = FAILED`, log. Never rethrows — the `User` row itself is
   never affected by a Sapo-side failure.
5. Save the `User`.

No deactivate/delete path — nothing to deactivate; users aren't hard-deleted
in FashionVista today, and admin bans are explicitly out of scope.

## Error Handling

- Push never throws back into the registration or profile-update request —
  either scheduled `afterCommit` or, for the non-transactional path, already
  running after the triggering `save()` has returned successfully. By
  construction it cannot affect the HTTP response of the call that
  triggered it.
- All Sapo-call exceptions are caught inside `doPush()` as
  `RuntimeException`, logged, and turned into `sapoSyncStatus = FAILED` —
  never rethrown, never surfaced to the async executor's default
  uncaught-exception handler.
- A `FAILED` status has no automatic retry (see Non-goals: no
  reconciliation). It's visible only by querying the `User` table directly
  — no admin surface is added for this in the current scope.

## Testing

- `SapoCustomerSyncServiceTest` — mock `SapoApiClient`: successful create
  (id stored, `SYNCED`), successful update (existing `sapoCustomerId` →
  `updateCustomer` called, not `createCustomer`), user not found → skipped
  silently, non-`CUSTOMER` role → skipped silently, API throws
  `RuntimeException` → `FAILED`, not rethrown, null/incomplete response →
  `FAILED`.
- `SapoNameSplitterTest` — two-word name splits correctly; multi-word name
  splits on the *last* space only; single-word name → `last` empty.
- `AuthServiceImplTest` — transaction-timing test asserting `pushCustomer()`
  is NOT invoked until after commit, same shape as
  `AdminVoucherServiceImplTest.createVoucher_WithinActiveTransaction_
  DefersPushUntilAfterCommit`.
- `UserControllerTest` — asserts `updateProfile()` calls
  `sapoCustomerSyncService.pushCustomer(user.getId())` directly (no
  synchronization wrapper) after `userRepository.save()`.
- No dedicated test for the two new `SapoApiClient` methods — consistent
  with the existing client, exercised indirectly through the service tests.
