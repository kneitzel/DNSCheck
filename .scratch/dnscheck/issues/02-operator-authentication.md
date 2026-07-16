# 02 — Operator authentication and session security

**What to build:** Operators authenticate with username and password; server-side sessions protect `/api/v1/` endpoints per the OpenAPI `session` security scheme. Login and logout endpoints are unauthenticated. JPA entities persist operators, groups, and rights (global: `admin`, `zone-create`; per-zone: `read`, `write`). Dev profile seeds default `admin`/`admin`; production bootstraps the first admin via environment variables when no operators exist. React login page establishes a session; logout clears it. Unauthenticated requests to protected endpoints receive an appropriate rejection.

**Blocked by:** 01 — OpenAPI pipeline and reconciliation API v1

**Status:** done

- [x] OpenAPI spec defines Auth-tagged login and logout paths with `session` security scheme on protected endpoints
- [x] JPA entities for operators, groups, and rights with global and per-zone grants
- [x] Session cookie authentication integrated with Quarkus security
- [x] Dev profile creates default `admin`/`admin` operator with `admin` global right
- [x] Production bootstraps first admin from environment variables when database has no operators
- [x] Reconciliation endpoints from ticket 01 require authentication
- [x] React login and logout UI using generated TypeScript client
- [x] REST Assured tests cover login, protected access, and logout
- [x] `mvn verify` passes
