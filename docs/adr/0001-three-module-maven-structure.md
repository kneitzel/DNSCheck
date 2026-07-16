# Three-module Maven structure

DNSCheck is split into three Maven modules under a parent POM:

- **`dnscheck-core`** (`de.neitzel.dnscheck.core`) — pure Java library: domain entities, JSON zone definition I/O, AXFR/`nsupdate` interaction, drift detection, and reconciliation. No Quarkus, no JPA.
- **`dnscheck-service`** (`de.neitzel.dnscheck.service`) — Quarkus application: scheduled reconciliation, REST API, operator authentication and rights (JPA), embedded React UI (`src/main/frontend`, MUI). Depends on `dnscheck-core`.
- **`dnscheck-cli`** (`de.neitzel.dnscheck.cli`) — standalone command-line tool for administrators to trigger reconciliation directly (e.g. after hand-editing a zone definition JSON). Uses `dnscheck-core` directly, not the service API.

This keeps DNS logic framework-agnostic and testable, allows the CLI to work without the service running, and lets the UI ship as part of the deployable service.
