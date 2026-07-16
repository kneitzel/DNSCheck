# Service and UI standards cleanup

Status: implemented (tickets 09, 10)

## Problem Statement

A full-codebase review against `CONTEXT.md` and project standards found several maintainability and vocabulary issues that do not block features but erode consistency: the React UI uses "Purge" for record deletion (discouraged domain language), zone services duplicate zone-catalog bootstrap logic, `OperatorRightsService` repeats grant-check patterns, the admin REST layer stacks `@Transactional` on both resource and service, and `App.tsx` is a monolith that hinders safe UI changes.

## Solution

Apply focused refactors and label fixes without changing operator-visible behavior (except UI copy). Extract shared catalog bootstrap, consolidate rights-check helpers, remove redundant transaction boundaries, rename UI actions to approved vocabulary, and optionally split `App.tsx` into feature components without changing routes or API usage.

## User Stories

1. As an operator reading the UI, I want record removal actions labeled consistently with domain terms ("Delete record"), so that language matches documentation and training.
2. As a developer adding a zone-scoped service, I want one shared way to resolve the zone catalog path and bootstrap the catalog, so that I do not copy-paste from three services.
3. As a developer enforcing rights, I want a single helper for "require write on zone" / "require read on zone", so that grant logic stays consistent and testable.
4. As a developer maintaining transactions, I want `@Transactional` on one layer of the admin stack, so that behavior is predictable and lazy-loading issues are handled once.
5. As a developer changing the zone view, I want UI code split by feature area (auth, catalog, zone view, admin), so that changes are reviewable and merge conflicts drop.
6. As a maintainer, I want `mvn verify` green after refactors with no API contract changes, so that this is safe cleanup.

## Implementation Decisions

### UI vocabulary

- Replace "Purge" with "Delete record" (or "Delete") on contextual record actions in zone view; align with CONTEXT.md deletion vocabulary.

### Zone catalog bootstrap

- Extract shared component (e.g. `ZoneCatalogSupport` or factory method on existing config) used by `ZoneCatalogService`, `ZoneViewService`, `ZoneRecordService` — single place for catalog directory resolution and `ZoneCatalog` construction.

### Operator rights

- Add package-private or public helpers on `OperatorRightsService`: `requireRead(zone)`, `requireWrite(zone)`, `requireAdmin()`, `requireZoneCreate()` throwing consistent `ForbiddenException` / domain exception mapped to 403.

### Transactions

- Keep `@Transactional` on `AdminService` (or resource, not both); ensure REST tests still pass without `LazyInitializationException`.

### Frontend structure

- Split `App.tsx` into components under `frontend/src/` (e.g. `LoginPanel`, `ZoneCatalogPanel`, `ZoneViewPanel`, `AdminPanel`, `ReconcileDashboard`) with props/callbacks; `App.tsx` holds routing state and layout only. No new state management library.

## Testing Decisions

### Primary seam: existing REST Assured suites

All `*ApiTest` must pass unchanged — refactors are behavior-preserving.

### Secondary: optional frontend smoke

Manual or existing Quinoa build in `mvn verify`; no new E2E framework required.

## Out of Scope

- Full design-system rewrite or MUI theme overhaul.
- Changing OpenAPI or REST contracts.
- Performance optimization of rights queries (indexes, caching).

## Further Notes

- Code review Standards axis findings; can ship independently of specs 09–13.
- Prefer doing UI label fix in the same PR as spec 11 if both touch zone view actions.
