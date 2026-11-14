# DNSCheck FX

A JavaFX desktop application for the zone catalog. It reads and writes the same zone definition files as the CLI, and it reconciles one catalog entry. It does not use the operator database or the HTTP API.

Views bind to ordinary Java models through the NeitzelLib `fx` module. That module's `fx/README.md` is the usage guide. This module is the DNSCheck call site.

## Install the fx library

`dnscheck-fx` depends on `de.neitzel.lib:fx:1.0-SNAPSHOT` from the local Maven repository. From the NeitzelLib checkout:

```bash
mvn -pl fx -am install -DskipTests
```

## Run

From the DNSCheck root, with Java 21 and the zone catalog and keys directories you want to edit:

```bash
mvn -pl dnscheck-fx javafx:run
```

`DNSCHECK_ZONES_DIR` and `DNSCHECK_KEYS_DIR` set the initial directories. Otherwise the fields start at `./zones` and `./keys`. Change them in the window and click **Reload**. Until Reload succeeds, **Open**,
**Dry-run file**, **Reconcile file**, and both **Save and …** actions reject changed
directory fields. A rejected action does not save the file or contact DNS.
Successful reload clears the old selection and editor; save any edits first.
A failed reload preserves the old catalog. Restore its directory fields to
continue using it. **Save** alone always writes the open editor’s original file.

## What you can do

- Reload the zone catalog. Entries are a table: owner, zone, nameserver, key file, valid, and error. Invalid zone files stay in the table with their error. Select a row, then **Open**. A column header sorts; its arrow filters.
- Open a catalog entry and edit the nameserver, the key file, and the records. The zone name stays the file name.
- Add or remove records in the record table. **Remove** is on the row. An empty TTL ignores TTL. `-1` uses the zone default. **Delete** stores a deletion marker: the pattern is kept and rdata is not. A column header sorts; its arrow filters.
- **Save** writes the zone definition JSON.
- **Save and dry-run** writes the file, then reports drift without sending `nsupdate`.
- **Save and reconcile** writes the file, then reconciles that catalog entry.
- **Dry-run file** and **Reconcile file** use the selected catalog entry as it is on disk. They do not write unsaved edits.

Reconciliation contacts the nameserver, the same way the CLI does. A failure stays on the notice line and in the text under it.

## Access and architecture

The desktop uses your filesystem and TSIG key access, like the CLI. It does not
apply the service’s operator login, MFA, or group/zone rights. See the
[module architecture](../docs/adr/0001-three-module-maven-structure.md) for the
shared-core boundary and catalog context rules.
