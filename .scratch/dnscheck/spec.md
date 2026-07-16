# DNSCheck

Status: ready-for-agent

## Problem Statement

Infrastructure tools such as Stalwart can remove or overwrite DNS records that operators depend on. There is no automated guard that continuously verifies authoritative DNS against a declared expected state and repairs drift. Operators also lack a unified way to inspect zone health, manage which records are protected by reconciliation, and trigger enforcement on demand after manual changes.

## Solution

DNSCheck is an infrastructure tool delivered as three Maven modules: a core library, a Quarkus service with an embedded React UI, and a CLI. It reads zone definitions from a zone catalog (JSON files on disk), compares them against the live zone (retrieved via AXFR), and reconciles drift through BIND `nsupdate` in a single batch per zone. Operators authenticate to the service to manage zone definitions, choose which records are enforced, inspect a zone view showing per-record status, and trigger reconciliation manually via API, UI, or CLI. The service REST API is contract-first: an OpenAPI document drives generated server interfaces and a generated TypeScript client for the UI.

## User Stories

1. As an infrastructure operator, I want DNSCheck to verify that managed records exist and are correct in the live zone, so that critical DNS entries are not silently removed or corrupted.
2. As an infrastructure operator, I want DNSCheck to automatically repair missing or wrong managed records via `nsupdate`, so that drift is corrected without manual BIND editing.
3. As an infrastructure operator, I want DNSCheck to run reconciliation on a schedule, so that drift caused by other tools is caught even when nobody is watching.
4. As an infrastructure operator, I want to trigger reconciliation immediately from the CLI after hand-editing a zone definition JSON file, so that I do not have to wait for the next scheduled run.
5. As an infrastructure operator, I want the CLI to use the core library directly without requiring the service to be running, so that emergency enforcement works during maintenance windows.
6. As an infrastructure operator, I want each zone defined in a JSON file in the zone catalog, so that expected DNS state is version-controlled and reviewable.
7. As an infrastructure operator, I want the zone name inside the JSON to match the filename, so that misnamed zone definition files are rejected at load time.
8. As an infrastructure operator, I want each zone definition to specify the nameserver and a reference to an external TSIG key file, so that AXFR and `nsupdate` are authenticated per RFC 2136.
9. As an infrastructure operator, I want TSIG key material stored in BIND native key syntax outside the zone definition JSON, so that secrets are not committed alongside zone data.
10. As an infrastructure operator, I want only records listed in the zone definition to be enforced, so that records added by other tools are not deleted by reconciliation.
11. As an infrastructure operator, I want to declare deletion markers in the zone definition that remove matching live records, so that unwanted records introduced by other tools can be purged deliberately.
12. As an infrastructure operator, I want deletion markers to match by name, type, and an optional rdata glob pattern, so that I can target specific bad records or all records at a name.
13. As an infrastructure operator, I want managed record names accepted as FQDN or relative to the zone (with `@` for the apex), so that zone definitions are easy to author and read.
14. As an infrastructure operator, I want optional TTL on managed records, so that I can ignore TTL, enforce an explicit TTL, or use the zone default TTL via the sentinel value `-1`.
15. As an infrastructure operator, I want the zone default TTL derived from the SOA minimum field retrieved via AXFR, so that default TTL tracks the authoritative zone configuration.
16. As an infrastructure operator, I want all `nsupdate` changes for one zone applied in a single session, so that reconciliation is atomic per zone.
17. As an infrastructure operator, I want reconciliation failures isolated per zone, so that one broken zone does not block reconciliation for all other zones.
18. As an infrastructure operator, I want a dry-run mode that reports planned changes without applying `nsupdate`, so that I can validate a new zone definition safely.
19. As an infrastructure operator, I want dry-run controlled by a service-level default with a per-run override, so that scheduled runs and manual triggers can behave differently.
20. As an infrastructure operator, I want the service to pick up manual JSON file changes on the next scheduled reconciliation run, so that git-ops and hand-editing workflows both work.
21. As an operator, I want to authenticate to the service with a username and password using server-side sessions, so that the React UI and API share a simple login flow.
22. As an operator with the global `admin` right, I want to manage other operators, groups, and rights assignments, so that access to DNSCheck is centrally controlled.
23. As an operator with the global `zone-create` right, I want to create new zone definitions in the zone catalog, so that new zones can be onboarded through the API.
24. As an operator with `read` on a zone, I want to view the zone view and reconciliation outcomes for that zone, so that I can inspect record status without making changes.
25. As an operator with `write` on a zone, I want to create, modify, delete, promote, demote, and trigger reconcile for that zone, so that I can manage DNS without editing JSON by hand.
26. As an operator, I want operator accounts, groups, and rights stored in a relational database, so that multi-user access is durable and queryable.
27. As an operator, I want the auth database to support H2, HSQL, PostgreSQL, and MariaDB/MySQL, so that DNSCheck fits different deployment environments.
28. As an operator, I want the first admin bootstrapped via environment variables in production, so that fresh deployments can be secured without manual database seeding.
29. As an operator developing locally, I want a default `admin`/`admin` account in dev only, so that local setup is frictionless.
30. As an operator, I want to create a record with optional enforcement, so that I can either protect it with reconciliation or apply it to live DNS only.
31. As an operator, I want enforced records written to the zone definition JSON and applied to the live zone, so that protection and live state stay aligned.
32. As an operator, I want non-enforced (ad-hoc) records applied to the live zone only, so that I can experiment without adding them to the zone definition.
33. As an operator, I want to promote an existing ad-hoc live record into the zone definition, so that I can start protecting a record that is already working in production.
34. As an operator, I want to demote an enforced record by removing it from the zone definition while keeping it in live DNS, so that I can stop protecting a record without deleting it.
35. As an operator, I want deletion to remove a record from live DNS and from the zone definition JSON if present, so that one action cleans up both stores.
36. As an operator, I want a zone view that merges live zone data and zone definition data with per-record status, so that the UI can show enforced, ad-hoc, drifted, missing, and deletion-marker states.
37. As an operator, I want to trigger reconciliation for all zones or a single zone via the REST API, so that automation and the UI can request enforcement on demand.
38. As an operator, I want to see the outcome of the last reconciliation run per zone, so that I can tell whether drift was found, corrected, or failed.
39. As an operator, I want full introspection of the zone catalog (loaded definitions, validation errors), so that configuration problems are visible before reconciliation runs.
40. As an operator, I want the REST API documented in OpenAPI and kept as the authoritative contract, so that the UI and future clients integrate against a stable, reviewable specification.
41. As a developer, I want server API interfaces and models generated from the OpenAPI spec at build time, so that implementation cannot drift from the documented contract.
42. As a developer, I want a TypeScript client generated from the same OpenAPI spec for the React UI, so that frontend calls are typed and stay aligned with the server contract.
43. As a developer working locally, I want Swagger UI available in the dev profile, so that I can explore and test API endpoints interactively.
44. As an operator, I want the REST API versioned under `/api/v1/`, so that future breaking changes can be introduced without disrupting existing integrations.
45. As an operator using the React UI, I want to log in and manage zones through MUI controls, so that routine DNS operations do not require CLI or JSON editing.
46. As an operator, I want the React UI served from the Quarkus service, so that deployment is a single application artifact.
47. As an infrastructure operator, I want the default reconciliation schedule interval to be 15 minutes with a configurable override, so that drift detection is timely without excessive AXFR load.

## Implementation Decisions

### Architecture (ADR-0001)

- Three Maven modules under parent `de.neitzel:dnscheck`:
  - **`dnscheck-core`** (`de.neitzel.dnscheck.core`) — pure Java library: domain model, zone definition I/O, AXFR/`nsupdate`, drift detection, reconciliation. No Quarkus, no JPA.
  - **`dnscheck-service`** (`de.neitzel.dnscheck.service`) — Quarkus application: scheduler, REST API, operator authentication and rights (JPA), embedded React UI with MUI via Quinoa.
  - **`dnscheck-cli`** (`de.neitzel.dnscheck.cli`) — Picocli executable using `dnscheck-core` directly.

### Persistence split (ADR-0002)

- Zone definitions (managed records, deletion markers, nameserver, key file reference) live as JSON files in a configured zone catalog directory.
- Operator accounts, groups, and rights live in a relational database (H2/HSQL/PostgreSQL/MariaDB).
- Both the reconciliation engine and the management API read and write the same JSON files for zone data.

### Reconciliation engine (`dnscheck-core`)

- **Primary orchestration interface:** `ReconciliationEngine` with `reconcileZone(zoneName, dryRun)` and `reconcileAll(dryRun)`, returning `ZoneReconciliationResult` / `ReconciliationRunResult` with `ZoneOutcome` (`SUCCESS`, `RECONCILED`, `DRIFT_DETECTED`, `FAILED`).
- **Zone catalog:** directory scan for `*.json`; each file is a zone definition; `zone` field must match filename stem.
- **Zone definition JSON schema:**
  - Top level: `zone`, `nameserver`, `keyFile`, `records[]`
  - Record entry: `name`, `type`, optional `rdata` (managed records), optional `rdataPattern` (deletion markers), optional `ttl`, optional `delete` flag
- **TTL policy:** omitted → ignore; `-1` → zone default TTL; positive integer → enforced
- **Zone default TTL:** SOA minimum from AXFR
- **Name normalization:** accept `@`, relative, or FQDN; normalize to FQDN internally
- **Deletion markers:** glob patterns (`*`, `?`); blank/omitted pattern matches all rdata for the name+type
- **Drift detection:** managed records → ADD if missing, UPDATE if TTL enforced and mismatched; deletion markers → DELETE for matching live records
- **DNS clients:** `LiveZoneClient` (AXFR via dnsjava `ZoneTransferIn` + TSIG), `NsupdateClient` (single `Update` batch per zone via dnsjava)
- **Key files:** BIND native syntax parsed to `TsigKey`; path relative to configured keys directory
- **Per-zone failure isolation:** one zone's error does not abort the run for other zones

*Already implemented in core:* models, zone catalog/store, key parser, drift detector, DNS clients, `DefaultReconciliationEngine`, unit tests.

### CLI (`dnscheck-cli`)

- Command: `dnscheck reconcile [zone] --dry-run --zones-dir --keys-dir`
- Uses `DefaultReconciliationEngine` directly; exit code 1 if any zone outcome is `FAILED`
- Environment variable defaults: `DNSCHECK_ZONES_DIR`, `DNSCHECK_KEYS_DIR`

*Already implemented:* command skeleton wired to engine.

### OpenAPI-first REST API (ADR-0004)

- **Contract-first:** a single OpenAPI 3.1 YAML document in the service module is the authoritative API contract. It is reviewed in PRs; server and client code are generated from it, not hand-written.
- **Spec shape:** single file with paths grouped by OpenAPI tags (`Auth`, `Zones`, `Reconciliation`, `ZoneView`, etc.).
- **Base path:** all endpoints under `/api/v1/`.
- **Authentication in spec:** OpenAPI 3.1 `session` security scheme on protected endpoints; login and logout are unauthenticated.
- **Server codegen:** Quarkus OpenAPI Generator produces JAX-RS interfaces and API model types during the Maven build. Generated server sources are not checked in.
- **Client codegen:** openapi-typescript-codegen produces a TypeScript client and DTO types during the frontend build. Generated client sources are not checked in.
- **Implementation pattern:** hand-written classes implement the generated JAX-RS interfaces and delegate to `dnscheck-core` and service-layer services. The React UI calls the generated TypeScript client.
- **Documentation exposure:** Swagger UI is enabled in the dev profile only. Production serves the OpenAPI JSON document at a stable URL but does not expose interactive Swagger UI.
- **Migration:** existing hand-written REST resources (currently under unversioned `/api/*`) will be replaced by this workflow as auth and zone management APIs are built.

### Service (`dnscheck-service`)

- **Configuration** via `DnsCheckConfig`: `dnscheck.zones.dir`, `dnscheck.keys.dir`, `dnscheck.dry-run`, `dnscheck.schedule.interval` (default `15m`)
- **Scheduler:** Quarkus `@Scheduled` triggers `reconcileAll` using service dry-run default
- **REST endpoints (minimum, under `/api/v1/`):**
  - `POST /reconcile?dryRun=` — manual reconciliation with per-run dry-run override
  - `GET /status` — last reconciliation run outcomes per zone
  - `GET /health` — liveness
- **Zone management API (to build, defined in OpenAPI):**
  - Zone catalog introspection
  - CRUD zone definitions
  - Record operations: create (with enforcement flag), modify, delete, promote, demote
  - Zone view: merged live zone + zone definition with `Record status` per entry
- **Authentication (to build, defined in OpenAPI):**
  - Server-side session cookies per `session` security scheme
  - JPA entities: operators, groups, rights (global: `admin`, `zone-create`; per-zone: `read`, `write`)
  - Production bootstrap: env vars for initial admin when no operators exist
  - Dev profile: default `admin`/`admin`
- **Frontend (to build):**
  - React + MUI served via Quinoa
  - Uses generated TypeScript client for all API calls
  - Login, zone list, zone view with record status and management actions

*Already implemented:* Quarkus scaffold, config, scheduler, placeholder hand-written REST under legacy `/api/*`, placeholder React page, H2 dev datasource. OpenAPI spec and codegen pipeline are not yet in place.

### Enforcement model (ADR-0003)

```
Record create:
  enforce=true  → write zone definition JSON + nsupdate live
  enforce=false → nsupdate live only (ad-hoc record)

Promote:  live record → copy into zone definition JSON
Demote:   remove from zone definition JSON; keep in live DNS
Delete:   nsupdate delete from live; remove from JSON if present
```

Zone view statuses: `enforced`, `ad-hoc`, `drifted`, `missing`, `marked for deletion`.

## Testing Decisions

### Primary seam: reconciliation

**`ReconciliationEngine`** is the highest seam for reconciliation behavior. Tests call `reconcileZone` / `reconcileAll` and assert on `ZoneReconciliationResult` outcomes and `plannedChanges` descriptions. DNS I/O (`LiveZoneClient`, `NsupdateClient`) is mocked or stubbed at this boundary so tests do not require a live BIND server.

This matches existing prior art in `DefaultReconciliationEngineTest` and should remain the default seam for new reconciliation scenarios.

### Secondary seam: service REST API

REST Assured integration tests against the Quarkus test profile assert HTTP status codes and response bodies against the OpenAPI contract. Prior art: `HealthResourceTest`. Auth, zone management, and session handling tests should authenticate via the login endpoint and exercise protected paths. When the OpenAPI spec defines response schemas, tests should validate that live responses conform to those schemas.

### Tertiary seams

- **Pure functions / small units** (no I/O): `DriftDetector`, `ZoneNameNormalizer`, `GlobMatcher`, `BindKeyFileParser`, `ZoneDefinitionStore` — tested directly with in-memory inputs. Prior art exists for each.
- **CLI:** optional smoke test invoking the shaded JAR with `--dry-run` against fixture zone catalog; lower priority than engine and REST seams.

### What makes a good test

- Assert external behavior (outcomes, HTTP status/body, CLI exit code) not internal call order.
- Use fixture zone definition JSON and key files in test resources.
- Do not test dnsjava protocol details; trust the thin DNS client wrappers with focused integration tests only if a BIND test fixture is added later.
- Do not test generated OpenAPI code; test that hand-written implementations satisfy the contract.

### Modules under test

| Module | Focus |
|---|---|
| `dnscheck-core` | Drift detection, reconciliation outcomes, zone definition I/O |
| `dnscheck-service` | OpenAPI contract conformance, auth, rights enforcement, zone management |
| `dnscheck-cli` | Command parsing and exit codes (light coverage) |

## Out of Scope

- Recursive/resolver-based DNS lookups (live zone is always AXFR from the authoritative nameserver)
- Full-zone mirroring (JSON as complete zone contents with deletion of all undeclared records)
- Regex rdata patterns (glob only)
- External identity providers (LDAP, OAuth) — local operators/groups only for v1
- Separate UI repository (UI ships inside `dnscheck-service`)
- CLI calling the service REST API (CLI uses core library directly)
- BIND zone file generation or direct zone file editing
- DNSSEC validation and signing management
- Multi-master conflict resolution beyond per-zone reconciliation batch
- Notification/alerting integrations (email, Slack) for drift events
- Hand-written REST endpoints not backed by the OpenAPI contract
- Checking generated server or client code into version control
- Swagger UI in production

## Further Notes

- Quarkus 3.37.1, Java 21, dnsjava for DNS protocol, Picocli for CLI, Jackson for JSON.
- Scheduler disabled in test profile to avoid missing zone catalog directory during unit tests.
- Remaining work is primarily in `dnscheck-service`: OpenAPI spec and codegen pipeline, operator auth, rights, zone management API, zone view, React UI with generated client — built on top of the existing `dnscheck-core` reconciliation engine.
- Domain glossary: `CONTEXT.md`. Architectural decisions: `docs/adr/0001`–`0004`.
- Canonical OpenAPI spec file: `dnscheck-service/src/main/resources/openapi.yml`.
