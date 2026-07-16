# 03 — Zone catalog introspection

**What to build:** Authenticated operators can list zones in the zone catalog and see validation errors for zone definitions that fail to load. Operators with `read` on a zone see that zone; listing respects rights assignments. API and React zone list page show zone names and any load-time errors (e.g. filename mismatch, invalid record type).

**Blocked by:** 02 — Operator authentication and session security

**Status:** done

- [x] OpenAPI spec defines Zones-tagged catalog introspection endpoints
- [x] Listing returns zone names and validation errors from `ZoneCatalog` scan
- [x] Rights enforcement: operators only see zones they have `read` (or global `admin`) on
- [x] React zone list page displays catalog contents using generated TypeScript client
- [x] REST Assured tests cover authenticated listing and rights denial
- [x] `mvn verify` passes
