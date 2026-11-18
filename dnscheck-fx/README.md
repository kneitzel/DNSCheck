# DNSCheck FX

A JavaFX desktop application for the zone catalog. It reads and writes the same zone definition files as the CLI, and it reconciles one catalog entry. It does not use the operator database or the HTTP API.

Views bind to ordinary Java models through the NeitzelLib `fx` module. That module's `fx/README.md` is the usage guide. This module is the DNSCheck call site.

## Build the desktop app

Use a full JDK 25 and Maven. The desktop module declares the public Gitea
Maven registry and uses NeitzelLib `1.0.1`, including the DataGrid viewport,
FXML declaration, grouping and saved setup support. Maven resolves the release
and its transitive library modules from that registry.

From the DNSCheck repository root:

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
mvn -pl dnscheck-fx -am -Djpackage.skip=true -DskipTests install
mvn -pl dnscheck-fx javafx:run
```

On startup, FX opens the last-used folder. If that folder is unavailable or
there is no history, FX opens a folder chooser. CLI and FX share the
[local directory format](../docs/cli.md). Keep flat `<zone>.domain` JSON files
and BIND keys together in the working folder. Several zones can use the same key.

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

A failed open or reload preserves the current catalog and editors. A readable
folder with no definitions opens as an empty catalog.

## Work with zones and keys

The left sidebar has vertical **Zones** (globe icon) and **Keys** (key icon) tabs.
Zones lists zone definitions and any validation errors; Keys lists valid key
filenames in the working folder. Each tab has a compact **+** button at the upper
right of its heading to create a zone or add a key.
Click a valid zone
to open it in a tab or return to its existing tab. Drag tabs to rearrange them,
split the editor area, or move them to floating windows. DNSCheck stores tab
arrangement, window positions, and sidebar width in `.dnscheck-layout.json` in
the working folder. The next time you open that folder, DNSCheck restores the
available tabs and loads live records as tabs become visible.

- **New zone** creates a definition in the working folder from a zone name,
  nameserver, and a key selected from the dropdown. It opens the new editor.
- **Add key** accepts pasted BIND key text and suggests a filename. You can also
  drop `.key` and `.domain` files onto DNSCheck. A zone imported before its key
  appears with a missing-key error until the key is available.
- DNSCheck asks before replacing a file and lists the tabs affected. Replacing a
  zone definition reloads its tab. Replacing a shared key refreshes live DNS for
  open zones using it.
- Change a zone’s key in the editor using the **Key file** dropdown, then save
  its settings.

Closing an edited tab, switching folders, and quitting offer **Save**,
**Discard**, and **Cancel**. **File → Reload** refreshes clean tabs and preserves
pending edits. If a definition changed on disk, use **Reload from disk** in its
editor to confirm discarding the pending edits and load the current file.

## Group, filter and arrange zone records

The records view declares NeitzelLib `DataGrid` directly in `Zone.fxml`.
Columns, widths, filters, page/group defaults and saved-setup schema IDs are
configured there. A `DirectBinding` shares `ZoneEditor.recordRows`, the merged
file/live view, with the grid's `items` property. `ZoneRecordGrid` supplies stable
keys, row appearance/actions and persistence startup; it does not construct the
control. The file-only editable `records` collection remains separate.

The records view uses NeitzelLib `DataGrid`. First-use defaults show 50 records per
page, grouped by Type with groups collapsed. Expand a triangle to inspect the
records. Group headings and counts describe the current page: the same type may
appear again on another page. Explicit All uses the existing large-result warning.
Collapsing a group does not change page counts or query live DNS.

Drag a header's right boundary to resize that column; release applies the width
and Escape cancels. Drag a header label to reorder columns or into the grouping
area to add a grouping level. Reorder the grouping chips to change nesting; ×
removes a level. All six columns are movable and groupable, including TTL as
policy text (Ignore, Zone default, numeric text, or a removal marker). Grouped
columns remain visible. Use **Grid setup → Column settings…** for keyboard
alternatives to dragging.

Use each header's filter button for text conditions or source-wide checklists.
Filters combine with AND and remain visible above the paging controls, including
when nothing matches. **Clear filters** removes them without changing grouping or
widths. Click a header label to toggle its source sort; groups themselves stay
ascending and rows inside each group retain source order. Grid Refresh only reruns
the local query; **Refresh live DNS** performs AXFR.

Double-click a visible row, press Enter on it, or choose **Record details…** to
open the existing editor. A record hidden by collapse keeps its selection key but
is unavailable to selection-based actions until visible again. SOA remains
read-only. Grouping, resizing, sorting and filtering do not modify zone definitions,
keys or live DNS.

## Saved grid setups

DNSCheck uses one application-wide saved record-grid setup across zones, working
folders and floating windows. It includes widths/order, grouping, expansion,
filters, sort and page size. A saved name filter can therefore hide every record
in a different zone; use the active-filter indication and Clear filters to recover.
Open tabs keep their own arrangement until **Grid setup → Reload saved setup**;
new tabs restore the latest successfully saved setup. The latest committed change
replaces the complete saved setup rather than merging fields from open tabs.

Committed changes save automatically after a short debounce. Settings status and
recovery controls appear separately from live DNS and reconciliation outcomes.
**Reset setup** transactionally restores current defaults. **Reset expansion** only
resets disclosure choices. Neither reload/reset nor a layout change requests AXFR.
At initial opening, restoration gates the first record query and the already
scheduled first live load. An unreadable/incompatible settings file keeps zone
settings accessible and offers Retry or explicit Reset before record loading.

Saved grid documents live under the shared native application configuration path:

- macOS: `~/Library/Application Support/DNSCheck/grids/`
- Windows: `%APPDATA%/DNSCheck/grids/` (with the native roaming fallback)
- Linux: `$XDG_CONFIG_HOME/DNSCheck/grids/`, or `~/.config/DNSCheck/grids/`

These JSON documents are separate from the existing history/theme `config.json`
and the working folder's `.dnscheck-layout.json`; neither is migrated. No row data,
selection, current page, TSIG credentials or unfinished record drafts are saved in
a grid setup. Expansion remembers current group paths plus a bounded inactive
history, so very old evicted paths may return to the collapsed default.

A competing application instance produces a settings conflict with explicit Reload
or Overwrite recovery. Failed writes keep the last good document and latest unsaved
setup. On quit, the existing record-operation and zone-edit checks run first,
followed by asynchronous settings flush. A failure offers Retry, Cancel quit, or
explicit Quit without saving grid settings. Closing a tab or switching folders
keeps the application persistence service alive.

`ZoneRecordGrid` is the consumer example: explicit scalar/group mappings,
source-wide filter providers, setup definition, per-grid persistence attachment,
initial loading gate and tab disposal. The NeitzelLib
[layout/persistence guide](../../NeitzelLib/fx/docs/data-grid-layout.md) describes
the public developer API.

## Shared themes

Choose **View → Theme → Light / Dark / High contrast**. DNSCheck uses the shared
NeitzelLib themes and its own semantic palette for enforced entries, removal
entries and live-only records. Their ✓ / − / ● indicators and text legend retain
their meaning under selection. Disabled retained rows stay readable and use a
dashed grid boundary. The record view uses NeitzelLib `DataGrid` and the existing
record dialogs; changing themes does not reload records, issue AXFR, save a zone,
or reconcile DNS.

Theme selection is explicit, independent of OS appearance. It is stored as `theme`
in the same native `config.json` as recent directories, preserving history and
other fields. It is not part of a per-folder docking layout. An unavailable saved
theme falls back to Light with an explanation. A detectable installation failure
retains the previous theme. A preference-write failure leaves the successfully
applied theme active and reports that it was not saved; malformed configuration
files are not overwritten.

Dialogs and docking floating windows inherit the selected bundle, including
windows opened afterward. Switching preserves selection, column widths/sorting,
dirty zone settings and record-dialog drafts. Non-color error text and independent
file-storage/online-action choices remain unchanged.

Application tokens in `de/neitzel/dnscheck/fx/theme/*.css` are
`-dns-file-surface`, `-dns-live-surface`, `-dns-removal-surface`,
`-dns-record-text`, `-dns-file-indicator`, `-dns-live-indicator`, and
`-dns-removal-indicator`. Existing `file-entry`, `live-entry`, `removal-entry`
row pseudo-classes and `file-legend`, `live-legend`, `removal-legend` classes remain.
`dns-error` is the application error-text hook. Domain tokens remain outside the
generic library. Custom overrides require their own contrast/layout qualification.

The desktop tests measure resolved row, selected/focused/disabled, legend,
record-dialog error and action/catalog icon colors for all three themes, and save
captures to `target/theme-evidence/`. Targets are 4.5:1 normal text, 7:1 high-contrast
text and 3:1 meaningful icons. This is bounded desktop rendering evidence, not a
full accessibility certification or a native-input/OS matrix. Existing JavaFX CSS
lookup warnings also occur in the pre-theme baseline; rendered specimens are
checked independently. The Service UI is unaffected by this desktop integration.

## Inspect and edit records

Showing a zone editor immediately displays its file entries and starts a background,
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
enforcement. **Dry-run file** and **Reconcile file** use the saved definition.
Hover over an action icon to see its name at the bottom of the main window.
Record dialogs operate on their selected entry. You can switch tabs while a record
operation runs. Closing the affected tab or switching folders waits for the result.

## Access and architecture

The desktop uses your filesystem and TSIG key access, like the CLI. It does not
apply the service’s operator login, MFA, or group/zone rights. See the
[module architecture](../docs/adr/0001-three-module-maven-structure.md) for the
shared-core boundary and catalog context rules.
