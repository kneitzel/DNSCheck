# 04 — Scoped zone catalog and derived rights

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Zone definitions live under scoped owner folders; the catalog, API, CLI, and UI identify zones by `(ownerFolder, zoneName)`. Filesystem placement is authoritative for ownership; effective read/write rights are computed from personal ownership, group membership, and global admin — without supplemental grants yet (ticket 05).

**Blocked by:** [01 — Foundation: owner folder model and zone grant schema expand](01-foundation-owner-folder-model.md)

**Status:** done

- [x] Zone catalog scans only direct `{name}.u/` and `{name}.g/` subfolders; flat root JSON ignored (optional warning)
- [x] Zone JSON stored as `{zonesDir}/{owner}/{zoneName}.json`; JSON `zone` field must match filename stem; owner folder created on first create
- [x] OpenAPI and all zone endpoints use `/api/v1/zones/{owner}/{zoneName}` (view, records, reconcile, deletion markers, catalog CRUD)
- [x] Derived rights: personal `.u/` owner gets read+write; all group members get read+write on `.g/` zones; global admin gets read+write on all entries
- [x] Zone create requires `zone-create` and target owner in own `.u/` or a member `.g/` (same rule for admin)
- [x] Each catalog entry reconciles independently; CLI accepts `dnscheck reconcile {owner}/{zoneName}`; no args reconciles all entries
- [x] All existing zone integration tests and frontend zone navigation updated to scoped paths
- [x] REST Assured tests cover personal and group zone create, list with owner, rights boundaries (403), and per-entry reconcile
- [x] `mvn verify` passes
