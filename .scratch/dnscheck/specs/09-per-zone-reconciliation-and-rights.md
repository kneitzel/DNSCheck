# Per-zone reconciliation and operator-scoped outcomes

Status: ready-for-agent

## Problem Statement

The master PRD promises that operators with `write` on a zone can trigger reconciliation for that zone, and that any operator can reconcile all zones or a single zone via the REST API. It also promises that operators with `read` on a zone can see reconciliation outcomes for zones they care about. The current service only exposes global `POST /api/v1/reconcile`, applies no per-zone rights checks, returns unfiltered zone outcomes on `GET /api/v1/status`, and defaults omitted `dryRun` to `false` instead of the configured service default. The React UI only triggers a global dry-run reconcile.

## Solution

Extend the Reconciliation API and UI so reconciliation can be triggered per zone or globally, with rights enforced consistently. Operators with `write` (or `admin`) may reconcile a specific zone; operators with `admin` may reconcile all zones. `GET /status` returns only zone outcomes the current operator may read. When the `dryRun` query parameter is omitted on manual reconcile endpoints, the service uses `dnscheck.dry-run` from configuration (matching the scheduler). The zone detail view gains reconcile controls (dry-run and live) for operators with `write` on that zone.

## User Stories

1. As an operator with `write` on a zone, I want to trigger reconciliation for that zone only via the REST API, so that I can enforce DNS after changes without reconciling unrelated zones.
2. As an operator with `write` on a zone, I want to choose dry-run or live reconciliation for that zone, so that I can preview or apply changes on demand.
3. As an operator with `admin`, I want to trigger reconciliation for all zones via the existing global endpoint, so that fleet-wide enforcement remains available.
4. As an operator with only `read` on a zone, I want reconciliation triggers for that zone to be denied, so that I cannot modify live DNS.
5. As an operator with only `read` on a zone, I want to see that zone's outcome in the last reconciliation run status, so that I can tell whether drift was found or corrected.
6. As an operator without `read` on a zone, I want that zone omitted from my status view, so that I do not see zones I am not authorized to inspect.
7. As an infrastructure operator, I want manual reconcile calls to respect the service dry-run default when I do not pass `dryRun`, so that scheduled and manual behavior stay aligned with deployment configuration.
8. As an operator using the React UI on a zone I can write, I want reconcile buttons on the zone view, so that I do not need the CLI or curl for on-demand enforcement.
9. As an operator using the React UI, I want global reconcile on the dashboard to remain available when I have `admin`, so that fleet operations stay in one place.
10. As a developer, I want per-zone and global reconcile endpoints documented in OpenAPI with correct response schemas and status codes, so that clients stay contract-first.

## Implementation Decisions

### API (OpenAPI, Reconciliation tag)

- Add `POST /api/v1/zones/{zoneName}/reconcile` with optional `dryRun` query parameter; returns the same reconciliation status shape as the global endpoint (or a zone-scoped variant containing a single `zoneOutcome` plus run metadata — choose one shape and document it; prefer reusing `ReconciliationStatusResponse` with a single entry for consistency).
- Global `POST /api/v1/reconcile` remains; requires `admin` global right (not merely any authenticated session).
- Per-zone reconcile requires `write` on the named zone (or `admin`).
- `GET /api/v1/status` filters `zoneOutcomes` to zones the operator may `read` (or `write`/`admin`); never leaks outcomes for ungranted zones.
- When `dryRun` query param is absent on either reconcile endpoint, use `DnsCheckConfig.dryRun()`; when present, use the explicit override. Update OpenAPI description to state this (default is configuration-driven, not a fixed `false`).

### Service layer

- Extend `ReconciliationService` with `reconcileZone(String zoneName, boolean dryRun)` delegating to `ReconciliationEngine.reconcileZone`.
- Inject `OperatorRightsService` for rights checks before reconcile and before returning status.
- Global reconcile: only zones the operator may write are reconciled unless `admin` (admin reconciles all catalog zones). Document this behavior in OpenAPI.
- Last-run status storage remains a single in-memory snapshot; filtered at read time for `GET /status`.

### UI

- Zone view: add "Dry-run reconcile" and "Reconcile" buttons when the operator has write on the selected zone (infer from successful zone view load + write attempt, or expose a lightweight capability if needed later).
- Dashboard: show global reconcile only when admin session can list operators or a dedicated check succeeds; alternatively always show and surface 403 — prefer hiding for non-admin.
- Display filtered status outcomes (already returned filtered by API).

### Architectural alignment

- ADR-0001: reconciliation logic stays in `dnscheck-core`; service adds rights and HTTP only.
- ADR-0004: OpenAPI is authoritative; implementations implement generated interfaces.
- Domain vocabulary from `CONTEXT.md`: reconciliation run, zone outcome, dry-run, managed record (unchanged).

## Testing Decisions

### Primary seam: `ReconciliationService` integration

Test rights filtering and dry-run default resolution in the service with a mocked or injectable `ReconciliationEngine` (or `@InjectMock` engine wrapper). This is the highest seam that covers operator rights + config default without HTTP noise.

### Secondary seam: REST Assured (`ReconciliationApiTest` pattern)

- Writer can reconcile their zone; reader gets 403 on per-zone reconcile.
- Non-admin gets 403 on global reconcile.
- Admin can global reconcile.
- `GET /status` returns only granted zones for a reader.
- Omitted `dryRun` respects `%test.dnscheck.dry-run` configuration.

Mock `LiveZoneLoader` / DNS at the engine boundary where existing zone tests do, so tests do not require BIND.

### What makes a good test

Assert HTTP status, filtered outcome lists, and effective dry-run behavior — not internal grant-query call order.

## Out of Scope

- Per-zone scheduled reconciliation intervals (fleet schedule remains global).
- Email/Slack notifications on drift.
- Reconciliation history beyond the last run snapshot.
- CLI changes (CLI already supports per-zone via core).

## Further Notes

- Code review finding: `ReconciliationResourceImpl` currently sets `effectiveDryRun = dryRun != null ? dryRun : false`.
- Blocked by: tickets 02 (auth), 04 (zone view UI placement). No new tickets required beyond this spec.
- Depends on existing `ReconciliationEngine.reconcileZone` in core.
