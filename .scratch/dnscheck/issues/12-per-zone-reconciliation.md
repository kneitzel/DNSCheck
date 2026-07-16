# 12 — Per-zone reconciliation (API + zone view UI)

**What to build:** Operators with `write` on a zone trigger dry-run or live reconciliation for that zone only via the REST API and zone view buttons. Readers are denied. Admin fleet-wide reconcile remains on the dashboard from ticket 11.

**Blocked by:** 11 — Reconciliation rights, dry-run default, and filtered status

**Status:** done

**Spec:** [09-per-zone-reconciliation-and-rights.md](../specs/09-per-zone-reconciliation-and-rights.md)

- [x] OpenAPI defines `POST /api/v1/zones/{zoneName}/reconcile` with optional `dryRun`
- [x] Per-zone reconcile requires `write` on the zone (or `admin`)
- [x] Zone view shows dry-run and live reconcile actions for writers
- [x] REST Assured tests: writer succeeds, reader gets 403, dry-run override works
- [x] `mvn verify` passes
