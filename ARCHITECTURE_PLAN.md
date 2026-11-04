# Android App: Architecture and Performance Plan

Goal: fast, smooth, and a foundation that can grow from "sales reps app" into a
multi-role product (sales + dispatch) sold to individual businesses.
Out of scope for now: security hardening, privacy, store compliance.

Status: PLAN ONLY. Nothing changed in code yet. Findings come from reading the code, not profiling.

## 0. Preconditions (do first)

- [ ] `git init` in the project and commit the current state as a baseline.
      The folder is NOT under version control today. A multi-step refactor without it is unsafe.
- [ ] 0b. Make `supabase/migrations/` a complete, ordered schema history (see section 5).
      Needed to onboard each new customer DB reproducibly.
- [ ] Add `.gitignore` coverage for `build/`, `.gradle/`, `local.properties`, `*.db*`.
- [ ] Confirm the app builds and the 8 existing unit tests pass (`./gradlew testDebugUnitTest`).
- [ ] Baseline numbers (record in the table at the bottom): cold start, full sync time,
      party list scroll jank, release APK size.

## 1. Target architecture

Today: one `:app` module, hand-wired in `SalesApplication`, role logic mixed in screens.

Target: feature modules over a shared core, so "dispatch" is a new module, not a fork.

```
:app                      shell: navigation host, DI graph, role-based entry
:core:common              Result type, dispatchers, time utils (java.time), constants
:core:model               pure Kotlin domain models (Party, Order, Product, VisitPlan ...)
:core:database            Room (entities, DAOs, migrations, schema export)
:core:network             Supabase client, DTOs, mappers, error mapping
:core:sync                sync engine (delta pull, outbox push), WorkManager workers
:core:auth                session, staff, roles/permissions
:core:ui                  design system (current ShadcnComponents), shared composables
:feature:parties          party list, beat/visit plan, store chat
:feature:orders           order form, order history, challan print
:feature:designs          design gallery
:feature:tracking         location service
:feature:dispatch         NEW later: pick/pack/dispatch/delivery workflows
```

Rules:
- Features depend on `core:*`, never on each other. Cross-feature navigation goes through
  route contracts in `core:ui` or `:app`.
- Domain models are separate from Room entities and DTOs. Mappers sit at the boundaries.
  (Today `PartyEntity` is used in 12 places including the UI and network layer.)
- DI with Hilt. Remove manual `lazy` wiring in `SalesApplication` and the hand-written ViewModel factories.
- Roles and permissions: a `Role`/`Capability` model in `core:auth`. Screens and nav
  entries are gated by capability (`CAN_BOOK_ORDER`, `CAN_DISPATCH`), not by string checks
  like `role.contains("admin")` (current `PartyListViewModel`).
- Multi-tenant ready: a `tenantId`/company in the session, sent with every request;
  config (company name, order prefix, challan layout, enabled modules) comes from the
  server, not constants. Move `SupabaseConfig` to `BuildConfig` or build flavors.

Do it incrementally (strangler pattern). Do not do a big-bang rewrite.
Module extraction can wait until step 4; steps 1-3 inside `:app` already pay off.

## 2. Work items

### Phase A: Foundation (no behavior change)
1. Version control + baseline (section 0).
2. Introduce Hilt; replace `SalesApplication` lazies and the `*ViewModelFactory` classes.
3. Add `Result`/sealed error type; `SupabaseClient` stops returning bare nullables.
4. Replace `SimpleDateFormat` with `java.time` behind a small clock/date helper
   (also makes ViewModels testable).
5. Enable Room schema export + `MigrationTestHelper` tests for 10->18.

### Phase B: Sync engine (biggest speed win)
6. One sync coordinator: `Mutex` + minimum interval; remove the duplicate triggers in
   `SalesApplication.onCreate`, `PartyListViewModel.init`, and `setSelectedTab`.
7. Delta pull: per-table `lastSyncedAt`, `updated_at > ts`, upsert; delete via tombstones
   or `is_deleted`. Stop wiping tables (`syncReplaceParties/Products`, `DELETE FROM designs`).
   Needs a small Supabase migration (`updated_at` + trigger on master tables).
8. Parallel table pulls (`async`/`awaitAll`).
9. Outbox pattern for pushes (orders, parties, visit plans, locations, voice notes):
   one `outbox` table, WorkManager worker with backoff, idempotency keys so retries
   cannot create duplicate orders.

### Phase C: Local data speed
10. Add indexes: `orders(orderTimestamp, isSynced, salesRepId)`,
    `daily_visit_plans(planDate, partyId, isSynced)`, `location_logs(timestamp, isSynced)`,
    `parties(isSynced)`.
11. Party search: debounce 250 ms, `flowOn(IO)`, then FTS4 if the party count is large.
12. Paging 3 for order history; `@Transaction` on multi-table writes.

### Phase D: UI smoothness
13. One immutable `UiState` per screen + one-shot events via `Channel`.
14. Stable `key`s in every `LazyColumn`; `@Immutable` UI models; `derivedStateOf`
    for filtered lists; `collectAsStateWithLifecycle` everywhere.
15. Split `ItemDetailsCard` (1006 lines), `OrderFormScreen` (607), `PartyListScreen` (545),
    `OrderFormViewModel` (802); one composable reads only the state it needs.
16. Compose compiler reports -> fix unstable params. Upgrade Kotlin/Compose (strong skipping).
17. Coil: explicit memory/disk cache, request thumbnails at display size.
18. Move PDF/HTML challan generation (1092 + 481 lines) off the main thread; split into
    layout model vs renderer so templates can be per-company.

### Phase E: Release performance
19. `isMinifyEnabled = true`, `isShrinkResources = true`, keep rules for Room/Gson DTOs.
20. Baseline Profile + Macrobenchmark (cold start, party list scroll).
21. Raise `compileSdk`/`targetSdk`, bump AGP, Compose BOM, OkHttp, WorkManager.

### Phase F: Module extraction, then dispatch
22. Extract `core:*` modules, then `feature:*` modules (one per PR, app builds each time).
23. Design the dispatch domain (shared with the web dashboard): dispatch group,
    challan -> dispatch, vehicle/transport, delivery status, proof of delivery.
    Confirm backend tables/RLS in `supabase/` before building UI.

## 3. Order and risk

| Order | Items | Risk | Payoff |
|---|---|---|---|
| 1 | 0, 1-5 | low | safety net, testability |
| 2 | 6-9 | medium (data correctness) | biggest speed and reliability gain |
| 3 | 10-12 | low | faster search, lists |
| 4 | 13-18 | medium | smoother UI |
| 5 | 19-21 | low | startup, size |
| 6 | 22-23 | high (large change) | dispatch-ready foundation |

Each item ships as a separate, buildable commit, with a test where logic changed.

## 4. Baseline vs after (fill in)

| Metric | Before | After |
|---|---|---|
| Cold start (ms) | | |
| Full sync duration (s) | | |
| Party list scroll jank (%) | | |
| Release APK size (MB) | | |
| Order form recompositions per keystroke | | |

## 5. Decisions (answered)

- **Tenancy:** one Supabase project (one DB) per customer. No `tenant_id` now.
  Consequence: per-customer differences live in config rows, not in code or schema forks.
  Onboarding a customer = run the migrations on a new project + seed config. This
  only works if `supabase/migrations/` is the full, ordered schema history.
  TODAY it is not: the folder holds 1 migration, `schema/` and `types/` are empty, and
  the real SQL is loose files in `dashboard/*.sql` plus `schema_dump.sql`.
  -> Work item 0b: consolidate into a baseline migration + ordered migrations.
- **Dispatch:** the `dashboard/` admin web app already owns the order lifecycle:
  `order_status: PENDING | CONFIRMED | DISPATCHED | DELIVERED | CANCELLED | REJECTED`
  (`dashboard/src/types/database.ts:106`) and a per-staff `order_status.dispatch`
  permission. Today "dispatch" is only a status change an admin makes in
  `OrderDetailsModal`; there is no dispatch data (transport, vehicle, LR/bilty no.,
  packed meters, delivery proof) and no dispatcher workflow.
  The Android app does not reference `order_status` at all.

## 6. Dispatch direction (recommendation)

Same Android codebase and same Supabase DB. Dispatch is a new `feature:dispatch` module
shown by role/capability, not a second app. The dashboard stays the admin/oversight
surface and reads the same tables.

- Role capability (`CAN_DISPATCH`) already exists in the permission JSON; reuse it
  and move it into `core:auth`.
- New backend tables, designed together with the dashboard (not app-only):
  `dispatches` (order_id, status, transport, vehicle_no, lr_no, dispatched_at,
  dispatched_by), `dispatch_items` (order_item_id, packed_meter, rolls/bales),
  `delivery_proofs` (photo, signature, receiver, geo). Partial dispatch means one
  order can have several dispatches; `order_status` becomes derived or stays as summary.
- Order status needs to reach the sales rep app (reps want to see "dispatched" on their
  history). Add `order_status` to the app's order sync as part of the Phase B sync work.
- Realtime (dashboard already has `useRealtimeSubscriptions`): dispatch events push to
  dashboard and rep app.
- Do this AFTER Phases A-C; dispatch is built on the new sync/outbox engine and
  module layout, otherwise it inherits the current sync problems.

## 7. Contract between app and dashboard

Two clients, one backend, hand-copied types (`dashboard/src/types/database.ts` vs Kotlin
DTOs in `SupabaseClient.kt`). Risk: silent drift (e.g. `order_status` already exists in
one and not the other).
- Make `supabase/migrations` the single source of truth.
- Generate TS types with `supabase gen types` into `supabase/types/` and use them in the
  dashboard. For Kotlin, keep DTOs in `core:network` with a test that fails when a
  column in the schema is missing from the DTO.
- Version RPC/view contracts; add new columns only (additive) so the older app APKs in
  the field do not break when the dashboard/DB is upgraded.
