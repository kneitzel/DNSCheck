# 07 — Zone key picker, missing keys, and in-use enforcement

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Zone create and edit integrate key selection and upload; operators see missing-key problems on the dashboard and at zone view/reconcile; key deletion is blocked when catalog entries reference the key, with client-facing count and server-side catalog entry logging.

**Blocked by:** [03 — Group keys management](03-group-keys-management.md), [04 — Scoped zone catalog and derived rights](04-scoped-zone-catalog-and-derived-rights.md)

**Status:** done

- [x] Selectable-keys read-only endpoint lists keys attachable to zones (own keys by filename; group keys as `groupname/filename`; no download URLs for group keys)
- [x] Zone create and zone settings support key picker plus upload (target: personal folder, or admin-chosen group folder)
- [x] Zone definition stores scoped `keyFile` path (e.g. `admin.u/example.com.key`)
- [x] Dashboard missing-key summary lists scoped paths and count of affected catalog entries (scoped to readable entries; admin sees all)
- [x] Zone view and reconcile return hard error when referenced key file is missing
- [x] Key delete returns error with in-use count when any catalog entry references the scoped path; server log lists `(ownerFolder, zoneName)` references
- [x] Frontend: key picker on zone create/edit; dashboard missing-keys panel
- [x] REST Assured tests cover selectable keys, missing-key summary, delete blocked with count, zone create with scoped keyFile
- [x] `mvn verify` passes
