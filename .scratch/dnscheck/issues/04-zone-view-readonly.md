# 04 — Zone view (read-only)

**What to build:** Operators with `read` on a zone see the zone view: live zone (AXFR) merged with the zone definition, with each record showing record status (`enforced`, `ad-hoc`, `drifted`, `missing`, `marked for deletion`). API and React read-only zone detail page. Includes zone-view merge logic that compares live zone records against managed records and deletion markers from the zone definition.

**Blocked by:** 02 — Operator authentication and session security; 03 — Zone catalog introspection

**Status:** done

- [x] Zone view merge logic produces per-record status from live zone and zone definition
- [x] OpenAPI spec defines ZoneView-tagged read endpoint for a single zone
- [x] Rights enforcement: `read` on zone required (or global `admin`)
- [x] React zone detail page shows records with status badges using generated TypeScript client
- [x] REST Assured tests cover zone view response shape and rights denial
- [x] `mvn verify` passes
