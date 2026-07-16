# 05 — Create record with optional enforcement

**What to build:** Operators with `write` on a zone can create a DNS record choosing enforcement. Enforced records are written to the zone definition JSON and applied to the live zone via `nsupdate`. Non-enforced (ad-hoc) records are applied to the live zone only. Zone view updates to reflect the new record and its status. API and React form with enforce toggle.

**Blocked by:** 04 — Zone view (read-only)

**Status:** done

- [x] Record create with `enforce=true` writes managed record to zone definition and updates live zone
- [x] Record create with `enforce=false` applies ad-hoc record to live zone only
- [x] OpenAPI spec defines record create endpoint with enforcement flag
- [x] Rights enforcement: `write` on zone required
- [x] React add-record form on zone view with enforce toggle
- [x] REST Assured tests cover both enforcement paths and rights denial
- [x] `mvn verify` passes
