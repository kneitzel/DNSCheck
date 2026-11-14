# CLI

The CLI and [desktop](../dnscheck-fx/README.md) work directly on one local directory.
They do not use service authentication, owner folders, or its separate zone/key directories.

```text
checkdns/
  example.com.domain
  example.net.domain
  dnscheck.key
```

Definitions are JSON named exactly `<zone>.domain`. The `zone` field must match
the filename. Each `keyFile` is a filename in the same directory, such as
`dnscheck.key`; several definitions may share a key. Keys use BIND native syntax.
Only immediate `.domain` files are scanned. Subdirectories and unrelated files
are ignored. Paths and symlinks escaping the selected directory are rejected.
Unknown JSON fields are rejected, including fields within records.

```json
{
  "zone": "example.com",
  "nameserver": "ns1.example.com",
  "keyFile": "dnscheck.key",
  "records": [
    { "name": "www", "type": "A", "rdata": "192.0.2.10", "ttl": 300 }
  ]
}
```

An omitted or null TTL ignores TTL; `-1` uses the live zone default and a positive
TTL is enforced. Deletion markers use `"delete": true` with an optional
`rdataPattern`. Records not managed by the definition remain untouched.

## Usage

```bash
dnscheck reconcile [ZONE] [--directory PATH] [--dry-run]
```

- `ZONE`: bare zone name, such as `example.com`. Omit to process all definitions.
- `--directory`: directory containing definitions and keys. Defaults to
  `DNSCHECK_DIRECTORY`, or the current directory when unset.
- `--dry-run`: retrieve live DNS and report drift without applying updates.

```bash
dnscheck reconcile --directory ./tmp/checkdns --dry-run
dnscheck reconcile example.com --directory /var/lib/dnscheck/local
dnscheck reconcile --help
```

Each outcome is printed under its bare zone name: `SUCCESS`, `RECONCILED`,
`DRIFT_DETECTED`, or `FAILED`. Planned changes are indented; errors go to stderr.
Invalid definitions and missing/invalid keys are reported individually. Other
valid zones continue. Exit code is 1 if any zone fails, the directory is empty,
or it cannot be read; otherwise it is 0. Argument errors return 2.

## Migration from the previous CLI/FX layout

The old `--zones-dir`, `--keys-dir`, `DNSCHECK_ZONES_DIR`, `DNSCHECK_KEYS_DIR`,
and `owner/zone` targets are no longer supported by CLI/FX. Copy the desired
JSON definitions and their keys into one directory, rename definitions from
`.json` to `.domain`, and replace scoped `keyFile` values with local filenames.
Resolve filename collisions explicitly when combining owner folders.
The service retains its scoped `.json` layout and its existing configuration.
CLI/FX no longer operate directly on service storage.

## Cron

```cron
0 2 * * * dnscheck /usr/bin/java -jar /usr/local/lib/dnscheck-cli.jar reconcile --dry-run --directory /var/lib/dnscheck/local >> /var/log/dnscheck-cron.log 2>&1
```

## Docker

Mount a local directory explicitly; the service's existing volumes are separate:

```bash
docker compose run --rm -v "$PWD/checkdns:/data/local" dnscheck cli reconcile --directory /data/local --dry-run
```

For authenticated automation against service storage, use its REST API instead
(`POST /api/v1/reconcile` or `POST /api/v1/zones/{owner}/{zoneName}/reconcile`).
