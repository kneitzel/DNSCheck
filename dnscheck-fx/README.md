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

`DNSCHECK_ZONES_DIR` and `DNSCHECK_KEYS_DIR` set the initial directories. Otherwise the fields start at `./zones` and `./keys`. Change them in the window and click **Reload**.

## What you can do

- Reload the zone catalog. Invalid zone files stay in the list with their error.
- Open a catalog entry and edit the nameserver, the key file, and the records. The zone name stays the file name.
- Add or remove records. An empty TTL ignores TTL. `-1` uses the zone default. **Delete** stores a deletion marker: the pattern is kept and rdata is not.
- **Save** writes the zone definition JSON.
- **Save and dry-run** writes the file, then reports drift without sending `nsupdate`.
- **Save and reconcile** writes the file, then reconciles that catalog entry.
- **Dry-run file** and **Reconcile file** use the selected catalog entry as it is on disk. They do not write unsaved edits.

Reconciliation contacts the nameserver, the same way the CLI does. A failure stays on the notice line and in the text under it.
