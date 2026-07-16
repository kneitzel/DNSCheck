# 11 — Reconciliation rights, dry-run default, and filtered status

**What to build:** Global reconcile requires `admin`. When `dryRun` is omitted on manual reconcile, the service uses the configured default. `GET /status` returns only zone outcomes for zones the operator may read. OpenAPI updated; dashboard global reconcile works for admin and is denied or hidden for others.

**Blocked by:** 09 — Backend prefactor: rights helpers and catalog bootstrap

**Status:** done

**Spec:** [09-per-zone-reconciliation-and-rights.md](../specs/09-per-zone-reconciliation-and-rights.md)

- [x] Global `POST /api/v1/reconcile` requires global `admin` right
- [x] Omitted `dryRun` query parameter resolves from service configuration
- [x] `GET /api/v1/status` filters `zoneOutcomes` to zones the operator may read
- [x] OpenAPI documents config-driven dry-run default and admin requirement
- [x] REST Assured tests cover rights denial, filtering, and dry-run default
- [x] Dashboard global reconcile available only for admin operators
- [x] `mvn verify` passes
