# Install

This guide covers installing DNSCheck from **pre-built release artifacts**. Building from source is out of scope here.

## Release artifacts

A release contains:

| Artifact | Path in archive | Purpose |
|----------|-----------------|---------|
| Service | `service/quarkus-run.jar` + `service/lib/` | Quarkus application |
| CLI | `cli/dnscheck-cli.jar` | Shaded CLI jar |

Requirements: **Java 25** (JRE or JDK).

## Zone catalog and keys

Before starting DNSCheck, prepare **scoped owner folders** for zone definitions and TSIG keys:

```bash
sudo mkdir -p /var/lib/dnscheck/zones/admin.u /var/lib/dnscheck/keys/admin.u
sudo cp docs/examples/zones/admin.u/example.com.json /var/lib/dnscheck/zones/admin.u/
sudo cp docs/examples/keys/admin.u/example.com.key /var/lib/dnscheck/keys/admin.u/
```

Edit the copied files for your nameserver and TSIG credentials. The `keyFile` field in a zone definition is a path relative to the keys directory (for example `admin.u/example.com.key`). See [Configure](configure.md) and [Migration](migration-scoped-ownership.md) if you are upgrading from a flat layout.

## Install the service

1. Unpack the release to `/opt/dnscheck`:

   ```bash
   sudo mkdir -p /opt/dnscheck
   sudo tar -xzf dnscheck-<version>.tar.gz -C /opt/dnscheck
   ```

2. Create an environment file `/etc/dnscheck/dnscheck.env`:

   ```bash
   QUARKUS_PROFILE=prod
   QUARKUS_DATASOURCE_DB_KIND=postgresql
   QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/dnscheck
   QUARKUS_DATASOURCE_USERNAME=dnscheck
   QUARKUS_DATASOURCE_PASSWORD=change-me
   QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION=update
   DNSCHECK_ZONES_DIR=/var/lib/dnscheck/zones
   DNSCHECK_KEYS_DIR=/var/lib/dnscheck/keys
   DNSCHECK_DRY_RUN=true
   ```

   Omit `DNSCHECK_BOOTSTRAP_ADMIN_*` on first start to auto-generate an `admin` password (logged once). Or set both variables to choose credentials explicitly.

3. Install the systemd unit:

   ```bash
   sudo cp docs/examples/dnscheck.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now dnscheck
   ```

4. Read the bootstrap password from the journal on first start:

   ```bash
   sudo journalctl -u dnscheck | grep "generated password"
   ```

5. Open `http://<host>:8080` and log in.

See [Configure](configure.md) for all settings and the [go-live](configure.md#go-live) step.

## Install the CLI

Copy `cli/dnscheck-cli.jar` anywhere on the PATH or invoke by full path:

```bash
sudo cp cli/dnscheck-cli.jar /usr/local/bin/dnscheck
sudo chmod +x /usr/local/bin/dnscheck
```

Verify:

```bash
dnscheck --help
dnscheck reconcile --help
```

The CLI reads the same zone catalog and keys directories as the service. Set `DNSCHECK_ZONES_DIR` and `DNSCHECK_KEYS_DIR`, or pass `--zones-dir` and `--keys-dir`. See [CLI](cli.md).

## Docker install

Docker is the recommended path when you want PostgreSQL bundled. From the repository or release tree:

```bash
mkdir -p zones/admin.u keys/admin.u
cp docs/examples/zones/admin.u/example.com.json zones/admin.u/
cp docs/examples/keys/admin.u/example.com.key keys/admin.u/
docker compose up --build
```

See [README](../README.md#quick-start-docker) for first-login steps.

### CLI via Docker

Use the same image with the `cli` entrypoint:

```bash
docker compose run --rm dnscheck cli reconcile --dry-run
docker compose run --rm dnscheck cli reconcile admin.u/example.com
```

Mount the same `zones` and `keys` volumes as the service container.

### Host networking

If the nameserver is only reachable from the host network stack, add to the `dnscheck` service in `docker-compose.yml`:

```yaml
network_mode: host
```

Remove the `ports:` mapping when using host networking. Ensure zone definitions reference a nameserver reachable from the container's network context.
