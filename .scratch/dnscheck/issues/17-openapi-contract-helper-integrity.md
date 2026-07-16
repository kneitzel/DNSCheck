# 17 — OpenAPI contract helper integrity

**What to build:** Golden-path API tests call a contract helper that fully enforces the OpenAPI contract in one step: resolve `operationId`, verify method and path template, require a documented success status (including 201 on create zone), then validate the response body schema. Wrong operation names, mismatched routes, or wrong status codes fail CI without relying on separate Hamcrest status checks.

**Blocked by:** None — can start immediately (ticket 16 is done)

**Status:** done

**Spec:** [15-openapi-contract-helper-integrity.md](../specs/15-openapi-contract-helper-integrity.md)

- [x] On load, helper builds an `operationId` → (method, path template) index from `META-INF/openapi.yml`
- [x] Unknown `operationId` fails with a clear message before schema validation runs
- [x] HTTP method or concrete path that does not match the indexed operation fails before schema validation
- [x] Path templates such as `{zoneName}` match concrete request paths in tests
- [x] Response status must be a documented 2xx for that operation (201 required for `createZone`)
- [x] Schema validation runs only after operation and status checks pass; failures include operationId, method, path, status, and validator details
- [x] Unit tests on the helper cover success and negative cases (wrong operationId, wrong status) without Quarkus
- [x] Existing golden-path `*ApiTest` contract checks still pass
- [x] Unused REST Assured validator adapter dependency removed if the core module suffices
- [x] `mvn verify` passes
