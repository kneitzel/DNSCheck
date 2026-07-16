# CLI

The `dnscheck` CLI reconciles live DNS against zone definition JSON files without running the full service. It does not use the operator database or HTTP API.

Use the CLI for ad-hoc runs, CI checks, or cron jobs on hosts that should not run the service. The service remains the primary control plane for scheduled reconciliation, the UI, and operator auth.

The CLI reads the same **scoped owner-folder** layout as the service (`{username}.u` / `{groupname}.g` under the zones and keys directories). See [Configure](configure.md) and [Migration](migration-scoped-ownership.md).

## Installation

Install `dnscheck-cli.jar` from a release (see [Install](install.md#install-the-cli)).

## Usage

```bash
dnscheck reconcile [CATALOG_ENTRY] [OPTIONS]
```

| Argument / option | Description |
|-------------------|-------------|
| `CATALOG_ENTRY` | Optional catalog identity `owner/zoneName` (e.g. `admin.u/example.com`). Omit to reconcile **all** catalog entries. |
| `--dry-run` | Detect drift without applying `nsupdate` |
| `--zones-dir` | Zone catalog directory (default `./zones` or `DNSCHECK_ZONES_DIR`) |
| `--keys-dir` | TSIG keys directory (default `./keys` or `DNSCHECK_KEYS_DIR`) |

Bare DNS zone names (for example `example.com` without an owner folder) are not valid catalog entry targets. Use `owner/zoneName`.

### Examples

Dry-run all zones:

```bash
dnscheck reconcile --dry-run
```

Reconcile one catalog entry live:

```bash
dnscheck reconcile admin.u/example.com --zones-dir /var/lib/dnscheck/zones --keys-dir /var/lib/dnscheck/keys
```

Help:

```bash
dnscheck --help
dnscheck reconcile --help
```

## Output

For each catalog entry, the CLI prints the catalog ref (`owner/zoneName`) and **zone outcome**:

| Outcome | Meaning |
|---------|---------|
| `SUCCESS` | No drift |
| `RECONCILED` | Drift was corrected |
| `FAILED` | Error (message on stderr) |
| `DRIFT_DETECTED` | Dry-run: changes identified but not applied |

Planned changes are listed as indented lines. Exit code **0** if no zone failed; **1** if any zone outcome is `FAILED`.

Flat JSON files at the zones directory root are ignored (same as the service). Optional log warnings may note ignored root entries during migration.

## Cron

Example `/etc/cron.d/dnscheck` — nightly dry-run report:

```cron
0 2 * * * dnscheck /usr/bin/java -jar /usr/local/lib/dnscheck-cli.jar reconcile --dry-run --zones-dir /var/lib/dnscheck/zones --keys-dir /var/lib/dnscheck/keys >> /var/log/dnscheck-cron.log 2>&1
```

Do not schedule CLI live reconciliation on the same interval as the service scheduler unless you intend overlapping passes.

## Docker

The release image supports a `cli` entrypoint:

```bash
docker compose run --rm dnscheck cli reconcile --dry-run
docker compose run --rm dnscheck cli reconcile admin.u/example.com
```

The Compose file bind-mounts `./zones` and `./keys` into `/data/zones` and `/data/keys`.

Run the service (default entrypoint):

```bash
docker compose up -d dnscheck
```

## API alternative

For authenticated automation against the running service, use the REST API (`POST /api/v1/reconcile`, `POST /api/v1/zones/{owner}/{zoneName}/reconcile`). See `/api/v1/openapi.json`.
