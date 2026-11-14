# DNSCheck

## AI disclosure

This codebase was produced entirely with AI assistance (Cursor). Human input was limited to prompts, review, and direction.

## Toolset

- [Cursor](https://cursor.com/)
- [Matt Pocock Skills](https://github.com/mattpocock/skills)

## License

This project is released under the [Unlicense](LICENSE) (public domain). You may use, modify, and distribute it for any purpose without restriction.

In addition to the Unlicense terms:

- The software is provided **as is**, with **no warranty** of any kind.
- The authors accept **no liability** for damages arising from use of this software.
- No representation is made that this work is **original** or **free of third-party intellectual property claims** (including patents, trademarks, or copyright).
- **You are responsible** for ensuring your use complies with applicable law in your jurisdiction.

---

DNSCheck verifies live DNS against zone definition JSON files and reconciles drift via BIND `nsupdate`. It ships as a **Quarkus service** (web UI, REST API, scheduled reconciliation), a **CLI** for ad-hoc or cron-based runs, and a **JavaFX desktop** application for the same zone catalog.

See [CONTEXT.md](CONTEXT.md) for domain terminology.

## Prerequisites

- **Java 25** runtime (for pre-built releases) or Docker
- **BIND** authoritative nameserver with TSIG configured for AXFR and dynamic updates
- Network access from DNSCheck to each zone's nameserver (TCP/UDP port 53)
- Zone definition JSON files and TSIG key files on disk

## Quick start (Docker)

1. Copy example zone and key files into scoped owner folders:

   ```bash
   mkdir -p zones/admin.u keys/admin.u
   cp docs/examples/zones/admin.u/example.com.json zones/admin.u/
   cp docs/examples/keys/admin.u/example.com.key keys/admin.u/
   ```

   Edit `zones/admin.u/example.com.json` and `keys/admin.u/example.com.key` for your environment (nameserver hostname, TSIG secret). The zone `keyFile` must be a scoped path such as `admin.u/example.com.key`.

2. Start the service and PostgreSQL:

   ```bash
   docker compose up --build
   ```

3. Read the generated admin password from the container logs on first start:

   ```bash
   docker compose logs dnscheck | grep "generated password"
   ```

4. Open the UI at [http://localhost:8080](http://localhost:8080) and log in as `admin`.

5. Review reconciliation outcomes (dry-run is enabled by default). When ready to apply live changes, see [Go live](docs/configure.md#go-live) in the configuration guide.

## Documentation

| Guide | Audience |
|-------|----------|
| [Install](docs/install.md) | Infrastructure operators — pre-built releases, systemd |
| [Configure](docs/configure.md) | Infrastructure operators — properties, env vars, database, scoped layout |
| [Migrating to scoped ownership](docs/migration-scoped-ownership.md) | Operators upgrading from flat `zones/` / `keys/` layouts |
| [Operator guide](docs/operator-guide.md) | DNS operators — web UI workflows |
| [CLI](docs/cli.md) | Automation — `dnscheck reconcile owner/zoneName`, cron, Docker |
| [Desktop](dnscheck-fx/README.md) | JavaFX — edit zone definitions and reconcile one catalog entry |

API contract: `GET /api/v1/openapi.json` (Swagger UI available in dev profile only).

## Components

| Artifact | Purpose |
|----------|---------|
| `dnscheck-service` | Long-running service: UI, API, scheduler, operator auth |
| `dnscheck-cli` | One-shot or cron reconciliation without the service |
| `dnscheck-fx` | JavaFX desktop application for the zone catalog |
| Docker image | Single image; default runs the service, `cli` entrypoint runs the CLI |

## Typical deployment

- Run the **service** as the always-on control plane (Docker Compose or systemd).
- Use the **CLI** on the same host or a jump box for ad-hoc or supplementary cron jobs against the same `zones/` and `keys/` directories.
- Avoid running the service scheduler and a CLI cron on the same interval without reason — both read the same zone catalog.
