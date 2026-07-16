# 01 — OpenAPI pipeline and reconciliation API v1

**What to build:** Contract-first API infrastructure works end-to-end for reconciliation. The OpenAPI 3.1 spec defines `/api/v1/` health, status, and reconcile endpoints. Server JAX-RS interfaces are generated at Maven build time via Quarkus OpenAPI Generator; a TypeScript client is generated at frontend build time via openapi-typescript-codegen. Generated code is not checked in. Hand-written implementations replace the legacy unversioned `/api/*` resources. Swagger UI is available in the dev profile. The React UI uses the generated client to display reconciliation status and trigger a reconcile run. REST Assured tests assert responses conform to the OpenAPI contract.

**Blocked by:** None — can start immediately.

**Status:** done

- [x] OpenAPI spec exists with Reconciliation-tagged paths under `/api/v1/` (health, status, reconcile with dryRun)
- [x] Quarkus OpenAPI Generator produces server interfaces at build time; generated sources are not committed
- [x] openapi-typescript-codegen produces TypeScript client at frontend build; generated sources are not committed
- [x] Hand-written implementations satisfy generated interfaces and delegate to `ReconciliationEngine`
- [x] Legacy `/api/*` hand-written REST resources are removed
- [x] Swagger UI works in dev profile; production serves OpenAPI JSON without interactive UI
- [x] React UI calls generated client for status and reconcile
- [x] REST Assured tests validate health, status, and reconcile responses against the contract
- [x] `mvn verify` passes
