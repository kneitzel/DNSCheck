# Deletion markers via API

Status: ready-for-agent

## Problem Statement

The master PRD and core library support deletion markers in zone definition JSON: records with a delete flag and optional rdata glob pattern that cause matching live records to be removed during reconciliation. Operators can only add deletion markers by hand-editing JSON. The zone view already shows `marked_for_deletion` status for matching live records, but there is no API or UI to create or remove deletion markers.

## Solution

Add API operations to declare and remove deletion markers in a zone definition, with `write` rights on the zone. Deletion markers are persisted to JSON only (no immediate nsupdate); reconciliation applies removals. React zone view allows marking live records for deletion and removing markers from the definition.

## User Stories

1. As an operator with `write` on a zone, I want to add a deletion marker for a name and type via the API, so that unwanted live records are purged on the next reconciliation.
2. As an operator with `write` on a zone, I want to optionally specify an rdata glob pattern on a deletion marker, so that I can target specific rdata values or all rdata at a name.
3. As an operator with `write` on a zone, I want to remove a deletion marker from the zone definition, so that I can stop targeting a record for removal.
4. As an operator with `read` only, I want deletion marker changes denied, so that I cannot alter enforcement policy.
5. As an operator, I want the zone view to reflect `marked_for_deletion` for live records matching a marker, so that I see what reconciliation will remove.
6. As an operator using the React UI, I want to mark a live record for deletion from the zone view, so that I do not edit JSON by hand.
7. As an operator using the React UI, I want to remove a deletion marker from the definition when I change my mind, so that reconciliation stops targeting that pattern.
8. As a developer, I want deletion marker operations documented in OpenAPI, so that the contract stays authoritative.

## Implementation Decisions

### API (OpenAPI, Zones tag)

- `POST /api/v1/zones/{zoneName}/deletion-markers` — body: `name`, `type`, optional `rdataPattern` (glob); writes marker to zone definition JSON; returns updated `ZoneViewResponse`.
- `DELETE /api/v1/zones/{zoneName}/deletion-markers` — body: `name`, `type`, optional `rdataPattern` to match marker identity; removes from JSON; returns updated `ZoneViewResponse`.
- Rights: `write` on zone (or `admin`).
- No immediate `nsupdate` on marker create/remove — behavior matches managed record enforcement (reconciliation applies).

### Core / service

- Build `RecordSpec` with `deletionMarker=true`, `rdataPattern` from request; use `ZoneDefinitionStore` write.
- Marker identity: name (normalized), type, pattern (empty pattern matches all rdata per existing core rules).

### UI

- Zone view actions for ad-hoc (and optionally drifted) live records: "Mark for deletion" opens dialog with optional glob pattern.
- For records already `marked_for_deletion` or definition-side markers visible in view: "Remove marker" action.
- Use domain term "Mark for deletion" in UI; avoid "Purge" (CONTEXT.md discourages purge for deletion domain language except as verb in prose — prefer "Mark for deletion" / "Remove marker").

## Testing Decisions

### Primary seam: REST Assured

Extend zone API test patterns with `@InjectMock LiveZoneLoader`; assert JSON file contains marker; zone view shows `marked_for_deletion`; reader denied.

### Secondary seam: core unit test

`ZoneDefinitionStore` round-trip for deletion marker entries (prior art exists in drift/zoneview tests).

## Out of Scope

- Immediate live DNS delete when adding a marker (reconciliation applies removals).
- Regex rdata patterns (glob only, per master PRD).
- Deletion markers on records not present in live zone (may add marker to JSON anyway — same as hand-edit).

## Further Notes

- Code review: US11–12 implemented in core/view only.
- Blocked by: ticket 04 (zone view), ticket 06 (record actions UI patterns).
