# HSQL auth database support

Status: ready-for-agent

## Problem Statement

The master PRD states the auth database must support H2, HSQL, PostgreSQL, and MariaDB/MySQL. The service ships JDBC drivers for PostgreSQL and MariaDB and documents H2 for dev/test, but HSQL is not configured or verified. Deployments that standardize on HSQL cannot run DNSCheck auth without undocumented customization.

## Solution

Add first-class HSQL support alongside existing databases: Quarkus datasource configuration profile for HSQL, documentation in application properties comments, and a test profile or integration test proving auth entities work on HSQL. No schema or entity changes required if Hibernate dialect is correct.

## User Stories

1. As a deployer, I want to point DNSCheck at an HSQL database via configuration, so that I can use HSQL in embedded or test deployments.
2. As a deployer, I want the same JPA entity model to work on HSQL without code forks, so that operator auth behaves identically across databases.
3. As a developer, I want an automated test that boots the service (or persistence layer) against HSQL, so that regressions are caught.
4. As a developer, I want example configuration for H2, HSQL, PostgreSQL, and MariaDB in comments or README fragment, so that switching databases is straightforward.

## Implementation Decisions

### Configuration

- Add `%hsql.*` or documented `quarkus.datasource.db-kind=hsql` profile snippet in `application.properties` (file URL or in-memory URL for tests).
- Ensure `quarkus-jdbc-hsqldb` dependency is present in service module POM (add if missing).
- Hibernate dialect: rely on Quarkus auto-detection for HSQL; override only if tests fail.

### Testing

- Add `@QuarkusTest` with `@TestProfile(HsqlTestProfile.class)` running a minimal auth flow: bootstrap admin, create operator, login — or Testcontainers-less in-memory HSQL file.
- Keep default `%test` profile on H2 to avoid slowing all tests; one dedicated test class is enough.

### Documentation

- Short comment block in application.properties listing four supported DB kinds and key property names.

## Testing Decisions

### Primary seam: Quarkus test with HSQL profile

Single test class: `AuthHsqlIT` or profile on `AuthApiTest` duplicate — prefer one focused IT to avoid duplicating full API suite.

Assert: login works, operator persisted across restart not required (single JVM test sufficient).

## Out of Scope

- Migration tooling (Flyway/Liquibase) for production schema management.
- Running reconciliation or zone tests against HSQL (auth only).
- Cloud-managed HSQL offerings configuration guides.

## Further Notes

- Code review: US27 partial — HSQL missing.
- Blocked by: ticket 02 (auth entities).
