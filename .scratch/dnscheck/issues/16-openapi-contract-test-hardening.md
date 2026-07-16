# 16 — OpenAPI contract test hardening

**What to build:** API integration tests validate golden-path responses against the published OpenAPI document via a shared helper. Contract drift (wrong fields, types, or status codes) fails CI.

**Blocked by:** 13 — Zone definition update, delete, and create 201

**Status:** done

**Spec:** [13-openapi-contract-test-hardening.md](../specs/13-openapi-contract-test-hardening.md)

- [x] OpenAPI response validator dependency and shared test helper added
- [x] Golden-path tests validate responses for Auth, Reconciliation, Zones, ZoneView, and Admin tags
- [x] Validator loads the same OpenAPI document the build publishes
- [x] Create zone and other known contract fixes asserted by schema validation
- [x] `mvn verify` passes
