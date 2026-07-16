# Scoped ownership for keys and zone catalog

Status: done

## Problem Statement

TSIG key files and zone definition JSON files currently live in flat directories. There is no per-operator or per-group ownership on disk, no key management UI, and zone access is tracked only in the database with no visible owner in the catalog layout. Operators cannot tell who owns a zone or a key, cannot manage keys through the service, and cannot organize shared infrastructure under group namespaces. Legacy flat layouts must be migrated manually to a consistent `{username}.u` / `{groupname}.g` convention shared by both keys and zones.

## Solution

Introduce scoped owner folders under the configured keys directory and zone catalog directory. Zone catalog entries and key files are stored under `{username}.u` or `{groupname}.g` subfolders. Filesystem placement is the authoritative source of zone and key ownership; effective zone rights are computed at runtime from owner folder, group membership, supplemental grants in the database, and global admin override. Add REST APIs and UI for key maintenance (personal keys under Account, group keys on group detail), zone create/edit with key picker and upload, missing-key dashboard summary, ownership transfer for zone definitions, and API routes keyed by `(ownerFolder, zoneName)`. The CLI targets catalog entries as `owner/zoneName`. Legacy flat files at catalog roots are ignored with optional warnings; migration is manual and documented.

## User Stories

### Keys — storage and references

1. As an operator, I want my TSIG key files stored under `{myUsername}.u/` in the keys directory, so that personal key material is clearly mine on disk.
2. As an admin, I want group TSIG key files stored under `{groupname}.g/` in the keys directory, so that shared credentials are grouped with the team namespace.
3. As an operator creating or editing a zone, I want the zone definition `keyFile` field to store a scoped path such as `admin.u/example.com.key` or `dns-team.g/shared.key`, so that key references are unambiguous.
4. As an operator, I want owner folders created automatically on first key upload or zone create in that scope, so that I do not need to create directories by hand.
5. As an operator migrating from a flat keys layout, I want documentation describing how to move key files into scoped folders and update zone JSON references manually, so that I can upgrade existing deployments without automated tooling.

### Keys — permissions

6. As an operator, I want full CRUD on key files in my own `{username}.u/` folder, so that I can manage my personal TSIG credentials.
7. As an admin, I want full CRUD on key files in any `{groupname}.g/` folder, so that I can manage shared group credentials.
8. As a global admin, I want to be unable to see or manage another operator's personal keys in their `.u/` folder, so that personal TSIG material stays private.
9. As a group member, I want to see group keys listed read-only on the group detail page, so that I know which shared keys exist without downloading them.
10. As a group member, I want group keys available in the zone key picker, so that I can attach shared credentials when creating or editing zones.
11. As a group member, I want to be unable to download group key files, so that shared secrets are not exposed outside admin control.
12. As an operator with write access on a key owner folder, I want to download that key file, so that I can use credentials locally when permitted.
13. As an operator, I want key deletion blocked when any zone definition in the catalog references that scoped key path, so that reconciliation is not left with broken references.
14. As an operator attempting to delete an in-use key, I want an error stating how many zones reference the key, so that I know deletion is blocked for a real reason.
15. As an admin troubleshooting deletion blocks, I want the server log to list the zone catalog entries that reference the key, so that I can identify what must be reassigned first.

### Keys — UI and API

16. As an operator, I want to manage personal keys on `/account/keys` with upload, raw BIND edit, replace via re-upload, download, and delete, so that key maintenance does not require shell access.
17. As an admin, I want to manage group keys on the group detail page with the same operations except where read-only rules apply for members, so that group credentials are managed in context.
18. As an operator uploading a key, I want filenames restricted to a safe basename ending in `.key`, so that path traversal and invalid names are rejected.
19. As an operator uploading a duplicate filename in the same owner folder, I want a conflict error, so that I must use Replace to overwrite an existing key intentionally.
20. As an operator editing a key, I want a raw BIND text editor validated on save, so that I can fix typos without delete-and-recreate.
21. As an operator, I want to replace a key by uploading a new file with the same filename, so that rotation is straightforward.
22. As an operator creating or editing a zone, I want to pick an existing key from a selectable-keys list or upload a new one, so that zone onboarding includes key setup.
23. As an operator uploading a key from zone create/edit, I want to choose whether the upload goes to my personal folder or (if I am admin) a group folder, so that shared keys can be created in the right namespace.
24. As an operator, I want own keys shown by filename only and group keys as `groupname/filename` in the picker, so that labels are readable.
25. As an operator, I want personal keys exposed via account-scoped REST endpoints and group keys via admin group endpoints, so that authorization follows the UI split.
26. As an operator creating a zone, I want a read-only selectable-keys endpoint that lists keys I may attach without exposing download URLs for group keys, so that the picker is safe for members.

### Keys — validation and dashboard

27. As an operator, I want a dashboard summary of missing key files for zones I can read, listing each missing scoped path and how many zones are affected, so that I can fix broken references early.
28. As a global admin, I want the missing-key dashboard to cover all zones in the catalog, so that I can see platform-wide key problems.
29. As an operator viewing a zone or triggering reconcile, I want a hard error when the referenced key file is missing, so that failures are explicit at point of use.

### Zone catalog — layout and identity

30. As an operator, I want zone definitions stored under `{username}.u/` or `{groupname}.g/` in the zone catalog, so that zone ownership is visible on disk alongside keys.
31. As an operator, I want the zone catalog scanner to include only direct child folders named `{identifier}.u` or `{identifier}.g`, ignoring flat JSON at the catalog root, so that the layout is consistent and legacy files do not silently load.
32. As an operator, I want each zone catalog entry identified by owner folder and zone name (e.g. `admin.u` + `example.com`), so that the same DNS zone name may exist under different owners when needed.
33. As an operator, I want API routes of the form `/api/v1/zones/{owner}/{zoneName}`, so that URLs match the on-disk layout.
34. As an operator, I want the JSON `zone` field to match the filename stem, so that misnamed files are rejected at validation.
35. As an operator, I want owner folders created on first zone create in that scope, so that I do not pre-create directories.
36. As an operator migrating from flat zone files, I want documentation for moving JSON into scoped folders and updating supplemental grants manually, so that upgrades are explicit.

### Zone catalog — ownership and rights

37. As an operator, I want filesystem owner folder to be the authoritative source of zone ownership, so that rights follow where the file lives.
38. As an operator with a zone in my `{username}.u/` folder, I want automatic read and write on that catalog entry, so that I control my personal zones without extra grants.
39. As a group member with a zone in `{groupname}.g/`, I want automatic read and write on that catalog entry, so that the team can manage shared zones collaboratively.
40. As a global admin, I want automatic read and write on every zone catalog entry regardless of owner folder, so that I can support and break-glass any zone.
41. As an admin, I want to assign supplemental zone grants keyed by owner folder and zone name, so that I can grant collaborators access to personal zones.
42. As an operator, I want effective zone rights computed at runtime from ownership, membership, supplemental grants, and admin status, so that moving a file on disk is reflected without stale grant rows for derived access.
43. As an operator with zone-create, I want to create zones only in my own `.u/` folder or a `.g/` folder where I am a member, including when I am admin, so that personal namespaces stay personal.
44. As an operator, I want each catalog entry reconciled independently even when multiple entries share the same DNS zone name, so that separate owner namespaces can coexist.
45. As an operator, I want reconciliation and CLI to target a specific catalog entry by `owner/zoneName`, so that I can reconcile one entry without ambiguity.

### Zone catalog — transfer and UI

46. As the owner of a personal zone, I want to transfer it to another owner folder via the API, so that I can move a zone to a group or another namespace without manual file editing.
47. As a global admin, I want to transfer any zone between owner folders, so that I can reorganize the catalog.
48. As a global admin, I want to be the only one who can transfer group-owned zones, so that members cannot move shared zones without admin approval.
49. As an operator transferring a zone, I want supplemental grants re-keyed to the new owner folder automatically, so that collaborators keep explicit access after a move.
50. As an operator, I want the zone list and detail UI to show owner folder, so that I can distinguish entries with the same DNS zone name under different owners.
51. As an operator creating a zone, I want to choose the owner folder (personal or a member group), so that the zone is created in the correct namespace from the start.
52. As an operator editing zone settings, I want to change nameserver and key file including picker and upload flows, so that rotation does not require JSON editing.
53. As a group member, I want to open group detail with a read-only keys section and see zones owned by that group in context, so that group infrastructure is visible in one place.
54. As a group member, I want read-only access to group detail for groups I belong to, with admin-only management actions hidden, so that I can inspect membership and keys without admin rights.

### CLI and operations

55. As an infrastructure operator, I want `dnscheck reconcile admin.u/example.com` to reconcile that catalog entry only, so that CLI targeting matches the API.
56. As an infrastructure operator, I want `dnscheck reconcile` with no zone argument to reconcile all catalog entries, so that full runs still work.
57. As an infrastructure operator, I want the CLI to continue managing keys only via the filesystem without new key subcommands, so that secret management stays in the UI/API.

## Implementation Decisions

### Shared owner-folder convention

- Both keys directory and zone catalog use direct child folders named `{identifier}.u` (operator) or `{identifier}.g` (group). Identifier matches the operator username or group name.
- Invalid folder names and flat files at the catalog root are ignored; optional warning in logs or admin-visible status.
- Lazy creation of owner folders on first key upload or zone create in that scope.

### Zone catalog (core library)

- Extend zone catalog scanning to iterate `{name}.u` and `{name}.g` subdirectories only; collect `*.json` one level deep per owner folder.
- Introduce a catalog entry identity type carrying `ownerFolder` (e.g. `admin.u`) and `zoneName` (DNS zone / filename stem). Uniqueness of filename is per owner folder; the same DNS zone name may appear under multiple owners.
- Update catalog introspection entries to expose owner folder, zone name, relative catalog path, validation state, and nameserver.
- Update zone definition create, read, update, delete, and transfer operations to address entries by `(ownerFolder, zoneName)` and resolve to `zonesDir/ownerFolder/zoneName.json`.
- Enforce JSON `zone` field equals filename stem on read and write.
- Add ownership transfer operation: move JSON file between owner folders; reject if target path already exists.
- Update reconciliation engine and CLI to accept catalog entry identity (`owner/zoneName`) in addition to or instead of bare DNS zone name for single-zone runs. Scheduled and full-catalog runs reconcile each discovered entry independently with no duplicate-DNS-zone conflict detection.

### Keys (core library + service)

- Introduce a key store abstraction over the keys directory that lists, reads, writes, and deletes key files under scoped owner folders with basename validation (`[a-zA-Z0-9._-]+`, must end with `.key`, no path separators or `..`).
- Parse uploaded or edited content with existing BIND key parser on save.
- Implement in-use detection: scan all zone definition JSON files in the catalog for matching scoped `keyFile` paths; return count for API errors and log full list of `(ownerFolder, zoneName)` references server-side on delete rejection.
- Implement missing-key detection for dashboard and zone operations: report scoped paths with count of affected catalog entries; scope dashboard results to readable entries for non-admin and all entries for admin.

### Rights and grants (service)

- Extend zone grant persistence with `ownerFolder` column (or equivalent composite identity); migrate supplemental grants from zone-name-only to `(ownerFolder, zoneName)`. Derived rights are not stored.
- Replace or extend `OperatorRightsService` with an effective-rights resolver that:
  - Grants admin read and write on all catalog entries.
  - Grants owner operator read and write on entries in their `{username}.u` folder.
  - Grants all group members read and write on entries in `{groupname}.g` for groups they belong to.
  - Applies supplemental zone grants from the database on top of derived rules.
- Key authorization: personal folder CRUD for owner only; group folder CRUD for admin only; group key list for members without download; download requires write on key owner folder.
- Admin cannot list or access other operators' personal keys.
- Zone create: require global `zone-create` and target owner folder in `{currentUser}.u` or a member `{groupname}.g` (same rule for admin).
- Zone transfer authorization: personal zones — owner or admin; group zones — admin only. Re-key supplemental grants on successful transfer.

### REST API (OpenAPI contract)

- Restructure zone paths from `/api/v1/zones/{zoneName}` to `/api/v1/zones/{owner}/{zoneName}` for catalog CRUD, records, view, reconcile, deletion markers, and transfer.
- Add `POST /api/v1/zones/{owner}/{zoneName}/transfer` with body specifying target owner folder.
- Extend create-zone request with required `owner` field (`admin.u` or `dns-team.g` pattern).
- Extend zone catalog list and entry schemas with `ownerFolder`, `zoneName`, and catalog path fields.
- Account keys: list, upload, get content, update content, replace upload, download, delete under `/api/v1/account/keys`.
- Group keys (admin write, member read list): under `/api/v1/admin/groups/{groupId}/keys` with parallel operations where appropriate.
- Selectable keys: read-only list for zone picker under a dedicated endpoint (e.g. `/api/v1/keys/selectable`) returning scoped paths and display labels without download URLs for group keys.
- Missing keys summary endpoint for dashboard (or extend status) returning paths and affected entry counts.
- Relax or split admin group GET detail so group members can read their group's detail and keys list; admin retains mutating operations.

### Frontend

- Add `/account/keys` page for personal key CRUD (upload, BIND editor, replace, download, delete, in-use indicator with count).
- Extend group detail page with keys section: admin CRUD; member read-only list.
- Extend zone create and zone settings with owner folder picker, key picker (existing + upload with target choice for admin), and scoped path display.
- Update all zone navigation and API calls to use owner + zone name from catalog entries.
- Dashboard panel for missing key paths and affected zone counts.
- Update admin zone grant UI to key grants by owner folder and zone name.

### Database migration

- Add `owner_folder` to zone grant entity; backfill or document manual migration for existing grants tied to flat layout zones.

### Documentation

- Document scoped layout for keys and zones, manual migration from flat directories, filename rules, and CLI `owner/zoneName` syntax in operator and configuration docs.

## Testing Decisions

### Primary seam: service REST API (REST Assured + OpenAPI contract validation)

Integration tests authenticate via login, exercise scoped zone routes (`/api/v1/zones/{owner}/{zoneName}`), key endpoints, selectable keys, transfer, missing-key summary, and rights enforcement (403/401 boundaries). Assert HTTP status, response bodies against OpenAPI schemas, and side effects on fixture directories under the test zones and keys dirs. This is the highest seam because it covers authorization, catalog layout, keys, and grant computation together.

Prior art: `ZoneCreateApiTest`, `ManagementApiTest`, `ZoneCatalogApiTest`, and `OpenApiContractSupport`.

### Secondary seam: zone catalog and key store (core unit tests)

Test scoped directory scanning, entry identity resolution, validation of zone/key filenames, transfer move semantics, in-use and missing-key scanning using temporary fixture directories. Prior art: existing `ZoneCatalog` and `ZoneDefinitionCreator` tests; extend rather than duplicate service-level coverage for pure filesystem logic.

### Tertiary seam: effective rights resolver (service unit tests)

Table-driven tests for derived rights (personal owner, group member, admin, supplemental grant, no access) given fixture operators, groups, and catalog entry identities. Keeps authorization rules testable without full HTTP stack.

### Reconciliation / CLI

Extend `DefaultReconciliationEngineTest` or CLI smoke tests only for catalog entry targeting (`owner/zoneName`) if not fully covered by API reconcile tests. Prefer API per-zone reconcile tests where possible to avoid a fourth seam.

### What makes a good test

- Assert external behavior: HTTP status and body, files present or absent on disk, forbidden access for wrong operator, conflict on duplicate upload, delete blocked with count when zone references key.
- Use dedicated test operators and cleanup helpers to avoid cross-test pollution (pattern from `ManagementApiTest`).
- Do not test generated OpenAPI stubs; test hand-written resource implementations and core catalog logic.

## Out of Scope

- Automated migration tools or dual-read of legacy flat keys/zones paths
- CLI key management subcommands
- Duplicate DNS zone conflict detection or merge during reconciliation
- Key or zone rename as a separate operation (replace upload or transfer covers rotation and ownership change)
- Audit log for key download or transfer
- Changing global admin inability to access other operators' personal keys

## Further Notes

- This spec combines two grilled design sessions: scoped TSIG key handling and scoped zone catalog ownership. Both share the `{username}.u` / `{groupname}.g` vocabulary documented in `CONTEXT.md` (Owner folder, Zone owner, Zone catalog entry, Supplemental zone grant).
- Implementing zone route changes is a breaking API change for existing clients; regenerate TypeScript client and update all frontend zone calls in the same delivery.
- Existing zone grants keyed only by DNS zone name require a migration story: document mapping flat zones to an owner folder during manual file move, or provide a one-time SQL migration note for dev/test databases.
- Group detail member access requires splitting read vs admin authorization on group GET endpoints; align with management pages pattern from the prior delivery.
- Server logs on blocked key delete should include catalog entry identities `(ownerFolder, zoneName)` referencing the key, not only DNS zone names, since catalog identity is scoped.
