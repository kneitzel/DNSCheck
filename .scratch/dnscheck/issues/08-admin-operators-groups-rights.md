# 08 — Admin: operators, groups, and rights

**What to build:** Operators with global `admin` right manage operators, groups, and rights assignments. Admin can create and disable operators, define groups, assign global rights (`admin`, `zone-create`) and per-zone rights (`read`, `write`). API and React admin UI. Does not overlap with DNS zone management — this is identity and access control only.

**Blocked by:** 02 — Operator authentication and session security

**Status:** done

- [x] OpenAPI spec defines Admin-tagged endpoints for operators, groups, and rights CRUD
- [x] Rights enforcement: global `admin` required for all admin endpoints
- [x] React admin UI for operator, group, and rights management
- [x] Changes to rights take effect on subsequent API requests
- [x] REST Assured tests cover admin operations and denial for non-admin operators
- [x] `mvn verify` passes
