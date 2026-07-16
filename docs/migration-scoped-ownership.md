# Migrating to scoped ownership

DNSCheck stores zone definitions and TSIG keys under **owner folders**: `{username}.u` for operators and `{groupname}.g` for groups. There is **no automated migration** from the older flat layout. Use this guide to move files by hand and update references.

Terms match [CONTEXT.md](../CONTEXT.md) (owner folder, zone catalog entry, supplemental zone grant).

## Target layout

Both directories use the same naming convention:

```text
zones/                          # DNSCHECK_ZONES_DIR
  admin.u/
    example.com.json
  dns-team.g/
    shared.example.json

keys/                           # DNSCHECK_KEYS_DIR
  admin.u/
    example.com.key
  dns-team.g/
    shared.key
```

| Rule | Detail |
|------|--------|
| Owner folder | `{identifier}.u` or `{identifier}.g`; identifier matches the operator username or group name (`[a-zA-Z0-9._-]+`) |
| Zone file | `{zonesDir}/{ownerFolder}/{zoneName}.json`; JSON `zone` field must equal `{zoneName}` |
| Key file | `{keysDir}/{ownerFolder}/{basename}.key`; basename `[a-zA-Z0-9._-]+`, must end with `.key` |
| Zone `keyFile` | Scoped path relative to the keys directory, e.g. `admin.u/example.com.key` |
| Catalog identity | `(ownerFolder, zoneName)` — e.g. `admin.u` + `example.com` |
| Flat root files | `*.json` or `*.key` directly under `zones/` or `keys/` are **ignored** and not loaded |
| Invalid folders | Direct children that are not `{identifier}.u` or `{identifier}.g` are **ignored** |

Owner folders are created automatically on first key upload or zone create in that scope. For migration you create them when you move files.

### Flat files and invalid folders after cutover

Anything left at the catalog or keys **root** (flat `*.json` / `*.key`) is ignored. Directories that do not match `{identifier}.u` or `{identifier}.g` are also ignored. DNSCheck does not dual-read the legacy flat layout. Optional warnings may appear in service or CLI logs for ignored root entries; treat leftovers as unfinished migration work and move or delete them.

## Before you start

1. Enable **dry-run** (`DNSCHECK_DRY_RUN=true`) if the service is running.
2. Back up `zones/`, `keys/`, and the operator database.
3. Decide the destination owner folder for each flat zone and key (personal `.u` vs group `.g`).
4. Plan downtime or a maintenance window if clients still call the old `/api/v1/zones/{zoneName}` routes (scoped ownership uses `/api/v1/zones/{owner}/{zoneName}`).

## Migrate keys

For each flat key file such as `keys/example.com.key`:

1. Create the owner folder if needed: `mkdir -p keys/admin.u`
2. Move the file: `mv keys/example.com.key keys/admin.u/example.com.key`
3. Keep the basename unchanged unless you also update every zone that references it

Example:

```bash
mkdir -p keys/admin.u keys/dns-team.g
mv keys/example.com.key keys/admin.u/example.com.key
mv keys/shared.key keys/dns-team.g/shared.key
```

Keys are managed in the UI under **Account → Keys** (personal) and **Admin → Groups → Keys** (group). The CLI does not add key subcommands; filesystem moves are expected during migration.

## Migrate zone definitions

For each flat zone file such as `zones/example.com.json`:

1. Create the owner folder: `mkdir -p zones/admin.u`
2. Move the JSON: `mv zones/example.com.json zones/admin.u/example.com.json`
3. Edit the file and set `keyFile` to the **scoped** path:

```json
{
  "zone": "example.com",
  "nameserver": "ns1.example.com",
  "keyFile": "admin.u/example.com.key",
  "records": []
}
```

4. Confirm `"zone"` still matches the filename stem (`example.com`).

Example:

```bash
mkdir -p zones/admin.u zones/dns-team.g
mv zones/example.com.json zones/admin.u/example.com.json
# edit zones/admin.u/example.com.json → "keyFile": "admin.u/example.com.key"
```

After restart (or the next catalog scan), the UI lists entries with an **Owner** column. The missing-keys dashboard reports any `keyFile` paths that do not exist on disk.

## Update supplemental zone grants

Supplemental grants are keyed by **`(owner_folder, zone_name)`**, not DNS zone name alone.

If you already have rows in `zone_grants` from a flat-layout era (zone name only, empty or missing `owner_folder`):

1. Map each grant's `zone_name` to the owner folder you chose when moving that zone JSON.
2. Set `owner_folder` accordingly.

Example (PostgreSQL / MariaDB; adjust table/column names if your schema differs):

```sql
-- After moving example.com.json into admin.u/
UPDATE zone_grants
SET owner_folder = 'admin.u'
WHERE zone_name = 'example.com'
  AND (owner_folder IS NULL OR owner_folder = '');
```

If Hibernate created `owner_folder` as `NOT NULL` on a fresh schema, recreate grants in the Admin UI (owner folder + zone name) instead of relying on empty values.

Derived rights (personal folder owner, group members, global admin) do **not** need database rows. Only supplemental grants need this update.

## Verify

1. Confirm no unmanaged flat files remain at `zones/` or `keys/` roots (or accept that they stay ignored).
2. Restart the service (or run the CLI against the same directories).
3. In the UI: zone catalog shows owner folders; missing-keys panel is empty for readable zones.
4. Dry-run reconcile:

   ```bash
   dnscheck reconcile --dry-run
   dnscheck reconcile admin.u/example.com --dry-run
   ```

5. Spot-check zone view and a per-zone reconcile for one migrated entry.

## Related docs

- [Configure](configure.md) — path layout and filename rules
- [CLI](cli.md) — `dnscheck reconcile {owner}/{zoneName}`
- [Operator guide](operator-guide.md) — UI workflows after migration
