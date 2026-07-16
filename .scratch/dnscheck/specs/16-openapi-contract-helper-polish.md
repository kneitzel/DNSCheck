# OpenAPI contract helper polish

Status: done

## Problem Statement

Ticket 17 hardened the contract test helper, but a follow-up code review found minor gaps: the OpenAPI document is parsed twice at class initialization, early failure messages omit the actual HTTP status, and unit tests do not cover HTTP method mismatch. Golden-path integration tests still duplicate `.statusCode(...)` Hamcrest checks the helper now enforces.

## Solution

Polish the shared contract helper and its unit tests so diagnostics fully match spec 15 user story 6, initialization loads the spec once, and negative coverage includes method mismatch. Optionally drop redundant status assertions from golden-path tests where the helper message is sufficient.

## User Stories

1. As a developer debugging CI, I want every contract failure message to include operationId, method, path, and actual HTTP status, so that I can diagnose mismatches without re-running tests.
2. As a maintainer, I want the helper to load and parse the published OpenAPI document once, so that test startup stays lean.
3. As a maintainer, I want a unit test proving wrong HTTP method fails, so that operationId binding is fully covered.
4. As a maintainer, I want golden-path API tests to rely on the helper for status enforcement where possible, so that assertions are not duplicated.

## Implementation Decisions

### Helper

- Parse `META-INF/openapi.yml` once; share the result between the operation index and the Atlassian interaction validator (or derive both from one load path).
- Include actual HTTP status in unknown-operation, method-mismatch, and path-mismatch failure messages (schema failures already include status).
- No change to the public four-argument API.

### Tests

- Add unit test: correct operationId and path but wrong HTTP method fails before schema validation.
- Rename misleading unit test name that suggests operationId mismatch when asserting path-template mismatch.
- Optional: remove `.statusCode(...)` from golden-path `*ApiTest` methods that immediately call `assertResponseConforms`, when failure messages remain clear.

## Testing Decisions

### Primary seam: `OpenApiContractSupport` unit tests

Extend existing unit tests; no Quarkus required.

### Secondary seam: existing golden-path `*ApiTest`

Must continue to pass after optional Hamcrest cleanup.

## Out of Scope

- New endpoints or OpenAPI schema changes.
- Error-response (4xx/5xx) validation in the helper.

## Further Notes

- Follow-up to ticket 17 code review (Standards: duplicate parse; Spec: partial user stories 2 and 6).
- Blocked by ticket 17 only.
