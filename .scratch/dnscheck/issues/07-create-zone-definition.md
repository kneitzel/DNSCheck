# 07 — Create zone definition

**What to build:** Operators with global `zone-create` right can create a new zone definition in the zone catalog (zone name, nameserver, key file reference). The new zone appears in the zone catalog list. Initial zone definition has no managed records. API and React "create zone" flow. Zone name must match the created JSON filename.

**Blocked by:** 02 — Operator authentication and session security; 03 — Zone catalog introspection

**Status:** done

- [x] Create zone writes a new zone definition JSON file to the zone catalog
- [x] Zone name in JSON matches filename; validation errors returned on mismatch
- [x] OpenAPI spec defines zone create endpoint
- [x] Rights enforcement: global `zone-create` (or `admin`) required
- [x] React create-zone form accessible from zone list
- [x] New zone appears in catalog introspection from ticket 03
- [x] REST Assured tests cover create and rights denial
- [x] `mvn verify` passes
