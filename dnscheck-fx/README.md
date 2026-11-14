# DNSCheck FX

A JavaFX desktop application for the zone catalog. It reads and writes the same zone definition files as the CLI, and it reconciles one catalog entry. It does not use the operator database or the HTTP API.

Views bind to ordinary Java models through the NeitzelLib `fx` module. That module's `fx/README.md` is the usage guide. This module is the DNSCheck call site.

## Install the fx library

`dnscheck-fx` depends on `de.neitzel.lib:fx:1.0-SNAPSHOT` from the local Maven repository. From the NeitzelLib checkout:

```bash
mvn -pl fx -am install -DskipTests
```

## Build the desktop app

With JDK 25 and NeitzelLib installed, run from the DNSCheck root:

```bash
mvn -pl dnscheck-fx -am package
```

The default `package` phase uses the build JDK's `jpackage` to create an app image
with its own Java runtime under `dnscheck-fx/target/app/`. On macOS, launch
`dnscheck-fx/target/app/DNSCheck.app`; Windows and Linux produce their native
app-image layout. Build on the target operating system. Rebuilding replaces the
generated app image. No packaging profile or separate install step is required.

Use `-Djpackage.skip=true` for a JAR-only build. The bundle version defaults to
`1.0` because macOS rejects a zero first version component; override it with
`-Djpackage.app.version=...`. The Maven artifact version remains unchanged.

## Run during development

From the DNSCheck root, with Java 25:

```bash
mvn -pl dnscheck-fx javafx:run
```

On startup, FX automatically loads the most recently opened folder that still
exists. If none remain, it uses the current working directory. An explicit
`DNSCHECK_DIRECTORY` overrides the remembered folder. CLI and FX share the [local directory format](../docs/cli.md):
flat `<zone>.domain` JSON files and BIND keys beside them, including shared keys.
The old separate directories and owner folders are no longer supported by FX.

Use **File → Open…** (Cmd/Ctrl+O) to choose a directory and load it immediately.
**File → Reload** (Cmd/Ctrl+R) rescans the currently loaded directory. The path
appears below the menu. **File → Recent** reopens one of the ten most recently
loaded directories. It is ordered newest first, deduplicates aliases, and checks
that each folder still exists whenever the submenu opens. Failed or cancelled
opens are not remembered.

History is saved automatically in a JSON configuration file:

- macOS: `~/Library/Preferences/DNSCheck/config.json`
- Windows: `%APPDATA%\DNSCheck\config.json` (fallback: `~/AppData/Roaming/DNSCheck/config.json`)
- Linux: `$XDG_CONFIG_HOME/dnscheck/config.json` (fallback: `~/.config/dnscheck/config.json`)

The `recentDirectories` array stores folder paths only, without definitions or
key contents. Configuration writes replace the file atomically when supported;
other settings in that JSON object are preserved. Configuration errors are
reported without preventing directory loading or overwriting a malformed file.

A failed open or reload preserves the previous catalog
and editor. An empty directory produces an explicit error.

The resizable split view has a navigation tree on the left:

- Select **Domains** to show only the catalog table on the right. It lists zone
  names, nameservers, key filenames, validity, and validation errors.
- Select a zone name beneath **Domains** to open its details on the right.
  Double-clicking a catalog table row also opens that zone.
- Invalid definitions stay in the tree and table; selecting one shows its error.

Before changing the selected zone, returning to **Domains**, opening/reloading a
directory, or closing the window would discard unsaved edits, FX offers **Save**,
**Discard**, and **Cancel**. Cancelling restores the previous tree selection and
keeps the current directory and editor. Save always writes the editor's original
file. A failed save keeps the editor open.

## Inspect and edit records

Opening a zone immediately shows its file entries and starts a background,
TSIG-authenticated AXFR from its configured nameserver. The transfer includes all
records, including SOA and DNSSEC records. A failed transfer leaves the file
entries visible and labels live state as unavailable. **Refresh live DNS** retries
the read; opening or refreshing a zone never changes DNS.

The table combines an exact file/live match into one row. Different rdata values
at the same name/type remain separate records. File-only entries and removal
markers without a live match remain visible. Every matching live value of a
removal pattern is shown. Colors and leading icons identify the source:

- Blue / **✓**: enforced file entry, including matching live records.
- Red / **−**: enforced removal entry.
- Green / **●**: live-only record.

The Status column distinguishes live matches, TTL drift, missing records and
unavailable live data. Colors have text and icon equivalents. SOA is displayed
read-only.

Double-click a row or choose **Record details…** to inspect it. The dialog shows
file intent and current live value/TTL. Choose **Edit** to enable its fields.
**New record…** opens the same dialog already in edit mode. There are independent
checkboxes for:

- **Store entry in domain file (enforce)**: store or replace the file entry.
  Unchecking this for an existing file entry removes its enforcement while
  leaving live DNS alone unless the online option is also selected.
- **Apply change to live DNS now**: apply only this record operation immediately.
  This starts unchecked every time. Online-only records are not enforced.

**Removal entry** stores a deletion marker when file storage is selected; with
online changes selected, it deletes matching live records immediately. An empty
pattern matches all rdata for that name and type; `*` and `?` are glob wildcards.
An omitted TTL ignores TTL during enforcement, `-1` selects the SOA default, and
a non-negative TTL enforces that value. An immediate edit with ignored TTL keeps
the existing live TTL; a new live record uses the zone default.

**Apply** validates the DNS name, type and rdata before writing or sending updates.
File-only operations work without an AXFR. Online operations read fresh live data,
replace only the selected old value (preserving other values in the same RRset),
then refresh the display. If a file changed externally, the operation asks for a
reload. File intent is saved first when requested; an online failure is reported
separately without undoing the saved file. An unconfirmed live state is cleared
until a successful refresh.

Save any edited zone settings before editing records. **Save zone settings**
writes the original `.domain` file and refreshes its live view. **Save and dry-run**
reports drift without applying it; **Save and reconcile** applies the full zone's
enforcement. Record dialogs operate on just their selected entry. Navigation and
closing wait while a record operation is being applied.

## Access and architecture

The desktop uses your filesystem and TSIG key access, like the CLI. It does not
apply the service’s operator login, MFA, or group/zone rights. See the
[module architecture](../docs/adr/0001-three-module-maven-structure.md) for the
shared-core boundary and catalog context rules.
