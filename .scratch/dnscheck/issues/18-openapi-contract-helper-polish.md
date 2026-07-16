# 18 — OpenAPI contract helper polish

**What to build:** Contract test helper loads the OpenAPI spec once, reports complete failure diagnostics (including actual HTTP status on every failure path), and unit tests cover HTTP method mismatch. Golden-path tests may drop redundant status assertions where the helper already enforces documented codes.

**Blocked by:** 17 — OpenAPI contract helper integrity

**Status:** done

**Spec:** [16-openapi-contract-helper-polish.md](../specs/16-openapi-contract-helper-polish.md)

- [x] OpenAPI document parsed once at helper initialization (no duplicate load/parse)
- [x] Early failures (unknown operationId, method mismatch, path mismatch) include operationId, method, path, and actual HTTP status
- [x] Unit test proves wrong HTTP method fails for a known operationId
- [x] Misleading unit test name corrected to reflect path-template assertion
- [x] Optional: redundant `.statusCode(...)` removed from golden-path tests that call the helper
- [x] `mvn verify` passes
