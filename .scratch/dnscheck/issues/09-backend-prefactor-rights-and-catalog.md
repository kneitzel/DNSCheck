# 09 — Backend prefactor: rights helpers and catalog bootstrap

**What to build:** Shared operator-rights helpers (`requireRead`, `requireWrite`, `requireAdmin`, `requireZoneCreate`), a single zone-catalog bootstrap used by all zone-scoped services, and one `@Transactional` boundary on the admin stack. No operator-visible behavior change; existing API tests stay green.

**Blocked by:** None — can start immediately

**Status:** done

**Spec:** [14-standards-cleanup.md](../specs/14-standards-cleanup.md)

- [x] `OperatorRightsService` exposes require* helpers that throw consistent 403 responses
- [x] Zone-scoped services use shared catalog bootstrap instead of duplicated setup
- [x] Redundant `@Transactional` removed from admin resource or service (one layer only)
- [x] All existing REST Assured tests pass unchanged
- [x] `mvn verify` passes
