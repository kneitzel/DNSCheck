# OpenAPI contract helper integrity

Status: done

## Problem Statement

Ticket 16 introduced shared OpenAPI response validation in API integration tests, but a post-implementation code review found the helper does not fully match spec 13. The four-argument overload accepts an `operationId` that is never checked, so a wrong operation name with a matching path and method would still pass. Status-code enforcement relies on separate Hamcrest assertions: the helper accepts any 2xx and does not verify the response matches the documented success code for that operation (for example 201 Created on create zone versus 200 OK). Contract-first CI should catch both schema drift and status-code drift through one helper call.

## Solution

Harden the shared contract test helper so `operationId`, HTTP method, request path, response status, and response body are all validated against the published OpenAPI document in one place. Golden-path tests keep calling the helper after REST Assured requests; tests should not need redundant status assertions once the helper enforces documented success codes.

## User Stories

1. As a developer, I want the contract helper to resolve `operationId` against the loaded OpenAPI document, so that call sites cannot pass a meaningless operation name.
2. As a developer, I want the helper to fail when the HTTP method or request path does not match the operation referenced by `operationId`, so that tests cannot validate the wrong endpoint by mistake.
3. As a developer, I want the helper to fail when the response status is not a documented success code for that operation, so that wrong codes such as 200 instead of 201 fail CI even without a separate Hamcrest status assertion.
4. As a developer, I want path matching to support OpenAPI path templates (for example `{zoneName}`), so that concrete request paths in tests match the spec's parameterized routes.
5. As a developer, I want schema validation to run only after operation and status checks pass, so that failure messages clearly distinguish wrong endpoint, wrong status, and wrong body.
6. As a developer, I want failure messages to include `operationId`, method, path, actual status, and validator diagnostics, so that CI output is actionable.
7. As a maintainer, I want the operation index built once when the helper class loads, so that per-test overhead stays low.
8. As a maintainer, I want a focused unit test on the helper that proves wrong `operationId` and wrong status fail without starting Quarkus, so that helper regressions are caught cheaply.
9. As a maintainer, I want existing golden-path API tests to keep passing with the stricter helper, so that ticket 16 coverage is preserved.
10. As a maintainer, I want test dependencies limited to what the helper actually uses, so that the build does not carry unused REST Assured adapter artifacts if the core validator suffices.

## Implementation Decisions

### Helper API

- Keep the public entry point `assertResponseConforms(operationId, method, path, response)` as the primary form used by golden-path tests. A method-only overload may remain for internal use or be removed if all call sites use `operationId`.
- On class initialization, load the published OpenAPI document from the same classpath location the build publishes (`META-INF/openapi.yml`) and build an index from `operationId` to HTTP method and path template.
- For each assertion:
  1. Look up `operationId`; fail if unknown.
  2. Assert the supplied HTTP method equals the operation's method.
  3. Assert the supplied request path matches the operation's path template (template parameters accept any non-empty segment).
  4. Determine documented success response codes for that operation (HTTP 2xx entries in `responses`). Fail if the actual status is not among them. When exactly one 2xx response is documented, treat that as the required code.
  5. Validate response body (and content type when present) with the existing Atlassian OpenAPI interaction validator against the matched operation and actual status.

### Dependency hygiene

- Prefer `swagger-request-validator-core` (or equivalent core module) if it supplies `OpenApiInteractionValidator` without the unused REST Assured adapter. Drop the REST Assured-specific artifact if nothing imports it after the change.

### Golden-path tests

- Existing `*ApiTest` classes that already call the helper require no new endpoints; update calls only if the API shape changes (for example dropping a redundant three-argument overload).
- Optional cleanup: remove redundant `.statusCode(...)` Hamcrest checks on golden paths where the helper now enforces status, but only when the helper failure message is equally clear.

## Testing Decisions

### Primary seam: `OpenApiContractSupport` unit tests

- Load the real published OpenAPI document from the test classpath.
- Build a synthetic REST Assured `Response` (or minimal stand-in) with a valid login response body and assert success for `login` + `POST` + `/api/v1/auth/login`.
- Assert failure when `operationId` is wrong but path/method match a different operation.
- Assert failure when status is 200 for an operation that documents only 201 (use `createZone` fixture body if needed).

### Secondary seam: existing REST Assured integration tests

- All golden-path tests from ticket 16 continue to call the helper and must pass under stricter checks.
- No new Quarkus integration tests required unless a unit-test seam cannot cover status enforcement.

### What makes a good test

- Test helper behavior through its public methods and realistic OpenAPI fixtures, not by reaching into private index structures.
- Do not start Quarkus for helper-only negative cases.

## Out of Scope

- Validating error responses (4xx/5xx) in the helper.
- Request body validation.
- Spectral linting of `openapi.yml`.
- Generating contract tests for every OpenAPI path.
- Changing production API behavior or the OpenAPI document content (unless a test proves the spec and implementation genuinely disagree — then fix implementation, not this spec's scope).

## Further Notes

- Follow-up to spec 13 / ticket 16 code review (Standards: unused `operationId`; Spec: partial helper contract).
- Blocked by nothing; ticket 16 must already be merged or present in the working tree.
