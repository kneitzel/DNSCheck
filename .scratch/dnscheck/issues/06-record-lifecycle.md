# 06 — Promote, demote, delete, and modify records

**What to build:** Full record lifecycle on the zone view. Promote copies an ad-hoc live record into the zone definition (enforced). Demote removes a record from the zone definition but keeps it in live DNS. Delete removes from live DNS and from the zone definition if present. Modify updates an enforced managed record in both stores. API and UI actions are available per record status. Rights enforcement requires `write` on the zone.

**Blocked by:** 05 — Create record with optional enforcement

**Status:** done

- [x] Promote adds existing live record to zone definition as managed record
- [x] Demote removes from zone definition; record remains in live DNS as ad-hoc
- [x] Delete removes from live zone via `nsupdate` and from zone definition JSON if present
- [x] Modify updates managed record in zone definition and live zone
- [x] OpenAPI spec defines promote, demote, delete, and modify endpoints
- [x] React zone view shows contextual actions per record status
- [x] REST Assured tests cover each lifecycle action and rights denial
- [x] `mvn verify` passes
