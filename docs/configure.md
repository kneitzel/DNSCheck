# Configure

This guide configures the service and its scoped storage. CLI and FX use the
[local directory format](cli.md) with `DNSCHECK_DIRECTORY` instead.


DNSCheck configuration uses Quarkus `application.properties`, environment variables, and an optional env file for systemd.

## Paths

| Property | Environment variable | Default | Description |
|----------|---------------------|---------|-------------|
| `dnscheck.zones.dir` | `DNSCHECK_ZONES_DIR` | `./zones` | Zone definition JSON catalog |
| `dnscheck.keys.dir` | `DNSCHECK_KEYS_DIR` | `./keys` | BIND-format TSIG key files |

### Scoped owner folders

Both directories use the same layout. Zone definitions and key files live under **owner folders** (`{username}.u` / `{groupname}.g`), not at the directory root. Flat root files and invalid folder names are ignored.

| Rule | Detail |
|------|--------|
| Zone file | `{ownerFolder}/{zoneName}.json`; JSON `zone` must match `{zoneName}` (`[a-zA-Z0-9.-]+`) |
| Key basename | `[a-zA-Z0-9._-]+`, must end with `.key` |
| Zone `keyFile` | Scoped path relative to the keys directory (e.g. `admin.u/example.com.key`) |

Owner folders are created on first key upload or zone create in that scope. Full layout examples and upgrade steps: [Migrating to scoped ownership](migration-scoped-ownership.md).

## Reconciliation

| Property | Environment variable | Default | Description |
|----------|---------------------|---------|-------------|
| `dnscheck.dry-run` | `DNSCHECK_DRY_RUN` | `false` | When `true`, detect drift but do not send `nsupdate` |
| `dnscheck.schedule.interval` | — | `15m` | Automatic reconciliation interval (service only) |

When the `dryRun` query parameter is omitted on manual reconcile API calls, the service uses `dnscheck.dry-run`.

## Go live

Start new deployments in dry-run mode. Review zone outcomes in the UI or via `GET /api/v1/status` before enabling live updates.

**Docker Compose:** set `DNSCHECK_DRY_RUN=false` in `docker-compose.yml` (or an override file) and restart:

```bash
docker compose up -d
```

**Bare metal / systemd:** set `DNSCHECK_DRY_RUN=false` in `/etc/dnscheck/dnscheck.env` and restart:

```bash
sudo systemctl restart dnscheck
```

Confirm a manual dry-run reconcile in the UI shows expected changes before going live.

## Database

The service stores operators, groups, and rights in a relational database. Supported engines: **H2**, **HSQL**, **PostgreSQL**, **MariaDB**.

### PostgreSQL (recommended for production)

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/dnscheck
quarkus.datasource.username=dnscheck
quarkus.datasource.password=change-me
quarkus.hibernate-orm.schema-management.strategy=update
```

Environment variable equivalents use the `QUARKUS_DATASOURCE_*` prefix (see Docker Compose in the repository root).

`schema-management.strategy=update` is a best-effort Hibernate schema update for development and small deployments. Prefer Flyway/Liquibase for production when schema changes become frequent.

If you upgraded to MFA while operators already existed and login fails with `Column "MFA_ENABLED" not found`, either restart on a build that includes `@ColumnDefault("false")` on that field, or apply once:

```sql
ALTER TABLE operators ADD COLUMN mfa_enabled BOOLEAN DEFAULT FALSE NOT NULL;
```

Supplemental zone grants store `owner_folder` with `zone_name`. When migrating from a flat catalog, update grant rows as described in [Migrating to scoped ownership](migration-scoped-ownership.md#update-supplemental-zone-grants).

### Other databases

Example JDBC URLs are documented in `dnscheck-service/src/main/resources/application.properties`.

## HTTP

| Property | Default | Description |
|----------|---------|-------------|
| `quarkus.http.port` | `8080` | Service listen port |

Public endpoints: `/api/v1/health`, `/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/openapi.json`. All other `/api/v1/*` routes require an authenticated session.

Zone routes are scoped: `/api/v1/zones/{owner}/{zoneName}/…` (for example `/api/v1/zones/admin.u/example.com/view`).

## Admin bootstrap

On first start when the operator table is empty:

| Case | Behavior |
|------|----------|
| Both `DNSCHECK_BOOTSTRAP_ADMIN_USERNAME` and `DNSCHECK_BOOTSTRAP_ADMIN_PASSWORD` set | Create that admin operator |
| Neither set | Create `admin` with a random password logged once at INFO |
| Only one set | Warn and create nothing |

After the first operator exists, bootstrap is skipped.

## BIND prerequisites

For each zone definition:

1. **Nameserver** — hostname or address DNSCheck uses for AXFR and `nsupdate`
2. **TSIG key file** — BIND native key syntax under an owner folder in the keys directory; referenced by scoped `keyFile` in the zone JSON
3. **BIND policy** — allow AXFR and dynamic updates for the key on that zone

DNSCheck must reach the nameserver on port 53 from its runtime environment (container bridge network by default; use host networking if required).

## Zone definition format

Example (`docs/examples/zones/admin.u/example.com.json`):

```json
{
  "zone": "example.com",
  "nameserver": "ns1.example.com",
  "keyFile": "admin.u/example.com.key",
  "records": [
    {
      "name": "mail",
      "type": "A",
      "rdata": "192.0.2.10"
    }
  ]
}
```

Managed records support optional `ttl` (positive value enforces TTL, `-1` uses zone default, omitted ignores TTL). Deletion markers use a `delete` flag with optional `rdata` glob pattern.

## Key file format

Example (`docs/examples/keys/admin.u/example.com.key`):

```
key "dnscheck-key" {
    algorithm hmac-sha256;
    secret "c2VjcmV0";
};
```

The key name and algorithm must match BIND server configuration. Prefer managing keys through the UI (Account keys / group keys) after the initial filesystem layout is in place.
