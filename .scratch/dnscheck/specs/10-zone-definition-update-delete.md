# Zone definition update and delete

Status: ready-for-agent

## Problem Statement

The master PRD describes CRUD for zone definitions in the zone catalog. Ticket 07 delivered create only. Operators cannot update nameserver or key file references, or remove a zone definition from the catalog, without hand-editing JSON on disk. The create endpoint also returns HTTP 200 while OpenAPI documents 201 Created.

## Solution

Add update and delete operations for zone definitions via the Zones API, with rights aligned to create (`zone-create` or `admin` for delete; update may use `write` on the zone or `zone-create`/`admin` — see decisions below). Fix create to return 201 per OpenAPI. React zone catalog gains edit/delete actions for authorized operators.

## User Stories

1. As an operator with `zone-create`, I want to update a zone definition's nameserver and key file reference via the API, so that I can fix configuration without shell access.
2. As an operator with `zone-create`, I want to delete a zone definition JSON file via the API, so that decommissioned zones are removed from the catalog cleanly.
3. As an operator with `admin`, I want the same update and delete capabilities, so that administration is not blocked when zone-create is unassigned.
4. As an operator with only `read` or `write` on a zone, I want update/delete of the zone definition denied, so that catalog structure stays under onboarding privileges.
5. As an operator, I want delete to remove only the JSON definition, not live DNS records, so that catalog removal is not confused with record deletion.
6. As an operator using the React UI, I want to edit nameserver and key file from the zone catalog, so that routine fixes do not require JSON editing.
7. As an operator using the React UI, I want to delete a zone definition with confirmation, so that mistakes are harder.
8. As a developer, I want create to return HTTP 201 with a `ZoneCatalogEntry` body matching OpenAPI, so that the contract is truthful.

## Implementation Decisions

### API (OpenAPI, Zones tag)

- `PATCH /api/v1/zones/{zoneName}` — body: optional `nameserver`, `keyFile` (zone name immutable; changing zone name equals delete + create).
- `DELETE /api/v1/zones/{zoneName}` — removes the JSON file; returns 204.
- Rights: `zone-create` or `admin` for PATCH and DELETE (catalog-level operations, consistent with create).
- `POST /api/v1/zones` — implementation returns 201 Created (fix `@ResponseStatus` or `RestResponse` as needed).

### Core / service

- Reuse `ZoneDefinitionStore.write` for patch after loading existing definition.
- Delete: remove file from catalog directory; 404 if missing.
- Do not AXFR or nsupdate on delete (definition-only).

### UI

- Zone catalog row actions: Edit (dialog), Delete (confirm dialog) visible when create-zone succeeds for the session (same gate as create button) or when admin panel loads.
- After patch/delete, refresh catalog list.

## Testing Decisions

### Primary seam: REST Assured (`ZoneCreateApiTest` / new `ZoneCatalogMutateApiTest`)

- Creator can patch nameserver; reader gets 403.
- Creator can delete; file absent from catalog introspection.
- Create returns 201.
- Patch on missing zone returns 404.

Use `target/test-zones` fixtures; no live DNS.

## Out of Scope

- Renaming a zone (change primary key) via API.
- Bulk import/export of zone catalog.
- Deleting live DNS when deleting a zone definition.

## Further Notes

- Code review: spec §Service "CRUD zone definitions" partially met.
- Blocked by: ticket 07 (create zone).
