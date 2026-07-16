# OpenAPI contract test hardening

Status: ready-for-agent

## Problem Statement

The master PRD and ticket 01 require REST Assured tests to validate responses against the OpenAPI contract. Current API tests assert selected JSON paths and status codes only. Contract drift (missing fields, wrong types, wrong HTTP status such as 200 vs 201 on create zone) can pass CI. The code review found create-zone returning 200 while OpenAPI specifies 201, and no schema validation anywhere.

## Solution

Introduce a repeatable pattern to validate representative API responses against the published OpenAPI document in tests. Fix known contract mismatches discovered in review. Keep tests at the REST seam — do not test generated server interfaces directly.

## User Stories

1. As a developer, I want API integration tests to fail when a response body violates the OpenAPI schema, so that contract-first development is enforced in CI.
2. As a developer, I want API integration tests to fail when documented HTTP status codes are wrong, so that clients can rely on the spec.
3. As a developer, I want schema validation applied to a curated set of endpoints per tag (Auth, Reconciliation, Zones, ZoneView, Admin), so that coverage is broad without brittle all-paths coupling.
4. As a developer, I want validation to use the same OpenAPI document the build publishes (`META-INF/openapi.yml` / build copy), so that tests track the real contract.
5. As a maintainer, I want a small test helper to reduce duplication across `*ApiTest` classes, so that adding validation to new endpoints is cheap.

## Implementation Decisions

### Validation approach

- Use OpenAPI response validation compatible with REST Assured (e.g. `openapi4j` OperationValidator, Atlassian OpenAPI validator, or SmallRye OpenAPI test utilities — pick one already compatible with Quarkus 3.37 / REST Assured 5).
- Helper: given OpenAPI spec loaded once per suite, `assertResponseConforms(operationId, method, path, response)` after authenticated requests. The helper must resolve `operationId`, verify method and path template match, enforce documented success status codes, then validate the body schema. See spec 15 for integrity requirements added after ticket 16 code review.
- Validate success responses (2xx) for golden-path tests; error responses optional in v1.

### Known fixes bundled in this work

- `POST /api/v1/zones` returns 201 (spec 10 may also fix; this spec owns test assertion either way).
- Reconcile `dryRun` OpenAPI description updated if behavior changes in spec 09 (test documents actual behavior).

### Scope of endpoint coverage (minimum)

- Auth: login 200 body
- Reconciliation: health, status, reconcile 200
- Zones: list, create, one record lifecycle response
- ZoneView: get zone view 200
- Admin: list operators 200

### CI

- `mvn verify` runs validation; no extra profile required.

## Testing Decisions

### Primary seam: REST Assured + OpenAPI validator (same as ticket 01 secondary seam)

This spec deepens the existing seam rather than adding a new one. Prior art: all `*ApiTest` classes; extend with shared `OpenApiContractSupport` test utility.

### What makes a good test

Validate full response body against schema for golden paths; keep separate assertions for business logic (rights denial 403) where body may be empty and schema allows empty error responses.

Do not validate generated TypeScript or generated JAX-RS interfaces.

## Out of Scope

- Automatic generation of tests from OpenAPI for every path.
- Request body validation in tests (response-only v1).
- Spectral linting of openapi.yml in CI (nice follow-up, not required here).

## Further Notes

- Code review Standards axis: hard gap vs spec.md §Testing Decisions.
- Can run in parallel with feature specs; no blocking dependency except existing API tests must pass.
- Post-implementation review (ticket 16): helper did not enforce `operationId` or documented success status. Follow-up spec: [15-openapi-contract-helper-integrity.md](15-openapi-contract-helper-integrity.md), ticket 17.
