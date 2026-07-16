# Zone view layout and ad-hoc record edit

Status: ready-for-agent

## Problem Statement

When an operator opens a zone view, the records table is too wide for a typical laptop screen. Action buttons sit off the right edge, so operators often only notice Promote, Mark for deletion, and Delete for ad-hoc rows and miss Modify on managed rows. Operators also cannot edit rdata or TTL on ad-hoc (live-only) records without promoting them into the zone definition first; they want to change live DNS for any editable live record while keeping Promote and Demote as separate enforcement actions.

## Solution

Make the zone view usable at laptop width: drop Expected rdata/TTL columns from the main table (surface expected values in the Edit dialog and a Status chip hint for drifted/missing), add horizontal scroll as a safety net, and move all per-row mutating actions into a compact overflow menu. Rename Modify to Edit. Extend the existing modify API so Edit works for ad-hoc records as a live-only rdata/TTL update (no zone definition write), while enforced and drifted records keep the existing JSON-plus-live modify behavior. Gate the Add record form and row mutations on zone write, matching reconcile.

## User Stories

1. As an operator with write on a catalog entry, I want the zone records table to fit on a laptop screen, so that I can see Name, Type, Status, Rdata, TTL, and Actions without hunting off-screen.
2. As an operator, I want Expected rdata and Expected TTL removed from the main table columns, so that the table is narrower and less cluttered for ad-hoc rows.
3. As an operator viewing a drifted or missing record, I want a Status chip hint that surfaces expected values, so that I still understand how live DNS differs from the zone definition without dedicated columns.
4. As an operator editing an enforced or drifted record, I want Expected rdata and Expected TTL shown in the Edit dialog, so that I can compare before changing live and managed state.
5. As an operator, I want horizontal scroll on the records table as a fallback, so that unusually long rdata still does not break the page layout.
6. As an operator with write access, I want all row mutations in a per-row overflow menu, so that action labels do not widen the table.
7. As an operator, I want the overflow menu labeled Edit (not Modify), so that the action matches everyday language.
8. As an operator with write access, I want Edit available on ad-hoc records, so that I can change live rdata or TTL without adding the record to the zone definition.
9. As an operator with write access, I want Edit available on enforced records, so that I can update managed records in the zone definition and live DNS together.
10. As an operator with write access, I want Edit available on drifted records, so that I can correct managed and live state from the same dialog.
11. As an operator, I do not want Edit on missing records, so that I am not offered an edit when there is no live record to change.
12. As an operator, I do not want Edit on marked-for-deletion records, so that marker and delete workflows stay the only actions for that status.
13. As an operator editing any allowed record, I want to change rdata and optionally TTL only, so that name and type remain stable identifiers (rename stays delete-plus-create).
14. As an operator editing an ad-hoc record, I want changes applied to live DNS only via nsupdate, so that the zone definition is not silently updated.
15. As an operator editing an ad-hoc record, I want an empty TTL field to leave the live TTL unchanged, so that I can fix rdata without touching TTL.
16. As an operator editing an ad-hoc record with a positive TTL, I want live DNS updated to that TTL, so that optional TTL edits still work without writing a TTL policy into JSON.
17. As an operator editing an enforced or drifted record, I want existing modify semantics preserved (zone definition TTL policy plus live update), so that managed-record behavior does not regress.
18. As an operator with write access, I want Promote still available on ad-hoc rows in the overflow menu, so that adding a record to the zone definition remains an explicit separate action.
19. As an operator with write access, I want Demote still available on enforced and drifted rows, so that removing enforcement without deleting live DNS remains explicit.
20. As an operator with write access, I want Mark for deletion, Delete record, Remove, and Remove marker to remain available for the same statuses as today, so that lifecycle coverage is not reduced by the menu change.
21. As an operator with only read on a catalog entry, I want the Add record form hidden, so that I am not invited to mutate DNS I cannot change.
22. As an operator with only read on a catalog entry, I want the row overflow menu either absent or without mutating items, so that I browse status without 403-prone controls.
23. As an operator with only read on a catalog entry, I still want to see the records table and Refresh, so that inspection remains possible.
24. As an operator with write access, I want Dry-run reconcile and Reconcile to remain write-gated as today, so that rights stay consistent across mutations.
25. As an API client, I want a single PATCH records operation that handles both managed and ad-hoc modifies, so that I do not need a second edit endpoint.
26. As an API client modifying an ad-hoc record, I want the response zone view to show the updated live rdata/TTL still as ad-hoc, so that I can confirm the record was not promoted.
27. As an API client modifying a managed record, I want the zone definition JSON updated as today, so that reconcile continues to enforce the new values.
28. As an API client without write, I want modify denied with 403, so that rights enforcement holds for the extended behavior.
29. As a developer, I want OpenAPI to describe that modify may target live-only ad-hoc records or managed records, so that the contract matches behavior.
30. As an operator reading docs later, I want the operator guide to say Edit and describe ad-hoc live-only edit, so that documentation matches the UI.

## Implementation Decisions

### UI (zone view)

- Table columns: Name, Type, Status, Rdata, TTL, Actions.
- Remove Expected rdata and Expected TTL columns.
- Wrap the table for horizontal overflow as a safety net.
- Status chip: for drifted and missing, expose expected values via tooltip or short secondary hint (not permanent columns).
- Actions column: single IconButton / menu (⋮) per row; no standalone action chips beside the menu.
- Menu contents by record status:
  - ad-hoc: Edit, Promote, Mark for deletion, Delete record
  - enforced / drifted: Edit, Demote, Delete record
  - missing: Remove (demote from definition)
  - marked_for_deletion: Remove marker, Delete record
- Rename UI label Modify → Edit.
- Edit dialog: rdata + optional TTL; for enforced/drifted also show expected rdata/TTL for comparison; name and type read-only.
- Gate Add record form and mutating overflow items on zone write (same effective write used for reconcile). Read-only: table + Refresh only.

### API

- Extend existing `PATCH /api/v1/zones/{owner}/{zoneName}/records` (modify).
- Request shape unchanged: identify record by name, type, current rdata; optional newRdata and/or newTtl.
- If the target is a managed record in the zone definition: keep current behavior (update definition + plan live changes).
- If the target is live-only (ad-hoc): plan live delete/add or TTL update only; do not write the zone definition; do not promote.
- Empty / omitted newTtl on ad-hoc: leave live TTL unchanged.
- Positive newTtl on ad-hoc: apply to live DNS only.
- Rights: zone write (or admin), unchanged.
- Update OpenAPI summary/description to cover both managed and ad-hoc cases.

### Core / service

- Extend record lifecycle modify path (or equivalent service branch) to resolve ad-hoc live records when no managed entry matches.
- Reuse existing nsupdate / PlannedChange application for live updates.
- Preserve promote, demote, delete, and deletion-marker behavior.

### Docs

- Update operator guide: Edit wording, ad-hoc live-only edit, overflow actions, write gating.

## Testing Decisions

### What makes a good test

Assert external behavior only: HTTP status and zone view body, on-disk zone JSON presence/absence of the record, and planned live changes via existing LiveZoneLoader / DnsUpdater mocks. Do not assert React component structure or menu DOM details.

### Primary seam: service REST API (REST Assured)

Preferred and sufficient for this feature. Extend zone record lifecycle API tests (prior art: `ZoneRecordLifecycleApiTest`, `ZoneRecordApiTest`) with scoped owner/zone paths:

- PATCH modify on an ad-hoc live record updates live view fields, leaves JSON without that managed record, status remains ad-hoc.
- PATCH modify on an enforced record still updates JSON and live (regression).
- Reader / non-write operator gets 403 on modify.
- Optional: empty newTtl on ad-hoc does not force a TTL change when only rdata changes.

### Secondary seam: core unit tests

Only if the ad-hoc modify branch is non-trivial in core and not fully exercised by API tests. Prior art: `ZoneRecordLifecycle` tests if present; otherwise prefer API coverage.

### Not in automated scope

Pixel-perfect layout, overflow menu styling, and Status tooltip copy. Manual check that the table fits a laptop width and that Expected columns are gone.

## Out of Scope

- Editing name or type in place (use delete + create).
- Edit dialog that also promotes into the zone definition.
- New dedicated “edit ad-hoc” HTTP endpoint.
- Card/list layout instead of a table.
- Changing Promote / Demote domain semantics or renaming them in CONTEXT.md.
- Visual regression / screenshot tests.
- Changing deletion-marker create/remove semantics.

## Further Notes

- Domain vocabulary: use Promote, Demote, Enforcement, Ad-hoc record, Zone view, Record status (CONTEXT.md). Prefer Edit in UI copy for the modify action; API operation may remain modify.
- Catalog identity remains `(ownerFolder, zoneName)` on scoped zone routes.
- This extends ticket 06 record lifecycle; it does not replace promote/demote.
- Grilling decisions: layout A (scroll + drop Expected columns + ⋮ menu); ad-hoc edit A (rdata/TTL live-only); statuses Edit for ad-hoc/enforced/drifted only; extend PATCH; empty ad-hoc TTL leaves live TTL; write-gate Add + menu.
