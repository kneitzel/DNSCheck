# Operator guide

This guide describes day-to-day use of the DNSCheck web UI. Terms match [CONTEXT.md](../CONTEXT.md).

## Login and logout

1. Open the service URL (default `http://localhost:8080`).
2. Enter your operator username and password.
3. Click **Login**.

On first deployment, use the `admin` account. The initial password is printed in service logs when auto-bootstrap runs.

Click **Logout** to end your session.

## Reconciliation dashboard

After login, the dashboard shows the latest **reconciliation run** status:

- Overall run state (`never_run`, in progress, completed)
- Per-zone **zone outcomes** you are allowed to read (`success`, `reconciled`, `failed`, `drift detected` in dry-run)
- **Missing keys** panel — scoped key paths referenced by readable catalog entries that are not present on disk (admins see all entries)

Operators without global admin see only zones where they hold `read` or `write` zone rights (derived from ownership/membership or supplemental grants).

**Reconcile all** (admin only) triggers a full reconciliation run. Use **Dry run** when you want to preview changes without applying `nsupdate`.

## Zone catalog

The catalog lists zone definitions discovered under scoped **owner folders** in the zone catalog directory (`{username}.u` / `{groupname}.g`). Each row shows owner folder, zone name, and validation state.

| Action | Required right |
|--------|----------------|
| View catalog | Any authenticated operator (entries filtered by effective rights) |
| Create zone | `zone-create` global right; target owner must be your `{username}.u` or a member `{groupname}.g` |
| Edit zone (nameserver, key file) | `zone-create` |
| Delete zone definition | `zone-create` |
| Transfer ownership | Personal zone: owner or admin; group zone: admin only |

To create a zone, choose the owner folder, zone name, nameserver, and a TSIG key (picker or upload). The zone JSON is stored as `{owner}/{zoneName}.json` with a scoped `keyFile` path (for example `admin.u/example.com.key`).

Filename rules (also in [Configure](configure.md)):

- Owner folders: `{username}.u` or `{groupname}.g`
- Zone names / JSON stems: `[a-zA-Z0-9.-]+`; JSON `zone` must match the filename stem
- Key basenames: `[a-zA-Z0-9._-]+` ending in `.key`
- Flat files and non-owner folders at the catalog or keys root are ignored

## Personal and group keys

- **Account → Keys** — CRUD on your `{username}.u/` key folder (upload, edit BIND text, replace, download, delete).
- **Admin → Groups → Keys** — admins manage `{groupname}.g/` keys; group members can list keys but cannot download them.
- Deleting a key fails if any zone catalog entry still references that scoped path; the error includes how many entries reference it.

## Zone view

Select a zone from the catalog to open the **zone view** — a merged picture of live DNS and managed records. If the referenced key file is missing, the view returns an error until you restore the key or update the zone's `keyFile`.

The records table shows **Name**, **Type**, **Status**, **Rdata**, and **TTL**. Expected values for drifted or missing records appear as a Status chip tooltip and again in the Edit dialog. Use horizontal scroll if rdata is unusually long.

Each record shows a **record status**:

| Status | Meaning |
|--------|---------|
| **enforced** | In the zone definition and matches live DNS |
| **ad-hoc** | Live only; not reconciled |
| **drifted** | In the zone definition but live DNS differs |
| **missing** | In the zone definition but absent from live DNS |
| **marked for deletion** | Deletion marker matches this live record |

### Record actions

With **write** on the catalog entry, the Add record form is shown and each row has an overflow menu (⋮) for mutations. Operators with **read** only see the table and Refresh.

| Action | Effect |
|--------|--------|
| **Create record** | Add a record to live DNS; optionally enforce (add to zone definition) |
| **Edit** | Change rdata or TTL. For enforced/drifted records, updates the zone definition and live DNS. For ad-hoc records, updates live DNS only. |
| **Promote** | Add an ad-hoc live record to the zone definition (enforcement) |
| **Demote** | Remove from zone definition; record stays in live DNS as ad-hoc |
| **Delete record** | Remove from live DNS via `nsupdate`; removed from zone definition if present |
| **Deletion marker** | Add a managed deletion rule (name, type, optional rdata glob) |

Write access is required for all mutating actions.

### Per-zone reconcile

Operators with `write` on a catalog entry can trigger reconciliation for that entry:

- **Dry run** — report drift without applying changes
- **Live** — apply `nsupdate` for that entry

When the dry-run toggle is omitted on API calls, the service uses the deployment default (`dnscheck.dry-run`). A missing key file fails the request with a hard error.

## Admin panel

Visible only to operators with the **`admin`** global right (group detail keys list is also available to members of that group).

### Operators

- List, create, enable/disable operators
- Disabled operators cannot log in

### Groups

- Create groups and add/remove members
- Grants assigned to a group apply to all members
- Group keys section for shared TSIG files under `{groupname}.g/`

### Global grants

| Kind | Effect |
|------|--------|
| `admin` | Full administration and all zones |
| `zone-create` | Create, edit, delete zone definitions (within allowed owner folders) |

### Zone grants (supplemental)

Supplemental grants are keyed by **owner folder and zone name** (not DNS zone name alone).

| Kind | Effect |
|------|--------|
| `read` | View zone view and reconciliation outcomes for that catalog entry |
| `write` | Manage records, promote/demote, delete, per-zone reconcile |

Grants can target an operator or a group. Derived rights from ownership and membership do not require a grant row.

## Rights summary

```
Global admin           → everything (except other operators' personal keys)
Global zone-create     → create/edit/delete zones in allowed owner folders
Personal/group ownership → read+write on that owner folder's zones
Supplemental zone read/write → extra access to a specific (owner, zone)
```

An operator can hold multiple grants. Zone rights are per catalog entry `(ownerFolder, zoneName)`.

## API access

Automation can use the REST API with the same session cookie established by `POST /api/v1/auth/login`. The OpenAPI description is at `/api/v1/openapi.json`. Zone paths use `/api/v1/zones/{owner}/{zoneName}/…`.

For command-line reconciliation without the service, see [CLI](cli.md). For upgrading from a flat on-disk layout, see [Migration](migration-scoped-ownership.md).
