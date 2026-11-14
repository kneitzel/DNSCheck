# Maven modules and desktop access

DNSCheck is split into four Maven modules under a parent POM. The original
core/service/CLI separation is retained; the desktop application is a fourth
consumer of the core:

- **`dnscheck-core`** (`de.neitzel.dnscheck.core`) — pure Java library: domain entities, JSON zone definition I/O, AXFR/`nsupdate` interaction, drift detection, and reconciliation. No Quarkus, no JPA.
- **`dnscheck-service`** (`de.neitzel.dnscheck.service`) — Quarkus application: scheduled reconciliation, REST API, operator authentication and rights (JPA), embedded React UI (`src/main/frontend`, MUI). Depends on `dnscheck-core`.
- **`dnscheck-cli`** (`de.neitzel.dnscheck.cli`) — standalone command-line tool for administrators to trigger reconciliation directly (e.g. after hand-editing a zone definition JSON). Uses `dnscheck-core` directly, not the service API.

- **`dnscheck-fx`** (`de.neitzel.dnscheck.fx`) — JavaFX desktop application using NeitzelLib Views and ViewModels over ordinary Java models. Reads and writes the same scoped JSON catalog and invokes the core directly, without HTTP or the operator database.

This keeps DNS logic framework-agnostic and testable, allows the CLI to work without the service running, and lets the UI ship as part of the deployable service.

## Desktop authority and catalog context

The desktop, like the CLI, runs with the local operator’s filesystem and TSIG key
access. Service login, MFA, and per-operator/group rights are enforced by the
service API only; the desktop does not provide that authorization boundary.

The directory fields are pending configuration until Reload successfully reads
the catalog. The applied zones/keys directory pair identifies the displayed
entries and open editor. Open and reconciliation require that the fields still
match this pair. Save and reconciliation validates the pair before writing, so a
changed field cannot save a file in one catalog and update DNS from another.
A failed reload leaves the old context intact; a successful reload replaces it
and clears the selection/editor. Save alone writes the editor’s original file.
Reconciliation captures the applied paths before starting its background worker.

## Build boundaries

The default Maven reactor includes the desktop. Install NeitzelLib as described
in the [desktop guide](../../dnscheck-fx/README.md) before building the full reactor.
The Docker image packages only service and CLI (`-pl dnscheck-service,dnscheck-cli
-am`), but copies all four child POMs because Maven discovers the full reactor
before selecting modules. The desktop and its JavaFX dependencies are not shipped
in that image. A regression test validates Maven using the Dockerfile’s initial
COPY inputs without requiring a Docker daemon or live DNS.
