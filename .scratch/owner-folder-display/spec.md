# Owner folder display

Status: ready-for-agent

## Problem Statement

Operators see owner folders in the UI with a `.u` or `.g` suffix (for example `admin.u` or `dns-team.g`). That encoding is useful on disk and in the API, but in everyday dashboard and admin screens it is noisy and easy to misread. Operators need to tell user-owned folders from group-owned folders at a glance without reading the suffix.

## Solution

Keep owner folders unchanged in storage, APIs, and form values. In read-only UI, show the bare identifier with a leading Material icon for owner kind (Person for user, Group for group), tinted so kind is visible without the suffix. Scoped key paths get the same treatment. Hovering reveals the exact owner folder or full scoped path. Invalid owner folder strings that cannot be parsed stay visible as raw text with no invented kind.

## User Stories

1. As an operator, I want the zone catalog Owner column to show the bare owner name without `.u` or `.g`, so that the table is easier to scan.
2. As an operator, I want a clear visual mark that an owner is a user versus a group, so that I do not confuse personal and team ownership.
3. As an operator, I want that mark to be an icon in front of the name, so that kind is visible before I read the text.
4. As an operator, I want user and group icons to use different tints, so that kind remains obvious even when I am not focused on the glyph shape.
5. As an operator, I want the same owner presentation in rights summaries, so that supplemental grants match how catalog owners look.
6. As an operator, I want the same owner presentation in zone-related headers and confirmations that display an owner for reading, so that ownership language is consistent across the dashboard.
7. As an operator, I want key picker labels that mention an owner folder to use the same presentation, so that upload targets are not cluttered with suffixes.
8. As an operator, I want missing-key scoped paths to show an owner-kind icon and bare owner plus key name, so that broken key references stay readable.
9. As an operator, I want hovering an owner display to show the exact owner folder string, so that I can still debug disk layout and API values.
10. As an operator, I want hovering a scoped path display to show the exact scoped path, so that I can copy or match what is on disk.
11. As an administrator, I want create-zone and transfer forms to keep accepting and showing full owner folder values, so that I can still type or paste `dns-team.g` when needed.
12. As an operator, I want API requests to continue using full owner folder strings, so that display changes never break zone or key operations.
13. As an operator, I want malformed owner folder strings that are not valid user or group folders to remain visible as raw text, so that I can find and fix bad catalog data.
14. As an operator, I want malformed owner folders not to get a fake user or group icon, so that the UI does not invent an owner kind.
15. As an operator, I want owner kind icons to have accessible names, so that assistive technology can announce user versus group.
16. As an operator, I want the bare identifier to match the identifier part of the owner folder, so that `admin.u` reads as `admin` and `dns-team.g` reads as `dns-team`.
17. As an operator, I want scoped path displays to keep a clear separator between owner and key basename, so that I can still tell which key file is meant.
18. As a developer, I want a single shared display helper for parsing and labeling owners, so that every screen does not reinvent suffix stripping.
19. As a developer, I want that helper to stay free of React rendering details where possible, so that owner-kind parsing can be tested without mounting the whole UI.
20. As an operator, I want placeholder and hint text in forms that still show example full owner folders, so that I know the real on-disk naming when editing.
21. As an administrator, I want dialog copy that lists a catalog entry for confirmation to use the improved owner presentation where it is read-only prose, so that delete and transfer confirmations stay consistent with the catalog table.
22. As an operator, I want no change to how zone ownership, rights, or key storage work, so that this remains a presentation improvement only.
23. As an operator, I want no change to reconciliation or live DNS behavior, so that display work cannot affect zone outcomes.
24. As a developer, I want the OpenAPI contract left alone for this change, so that server and generated clients stay stable.
25. As an operator, I want personal owner folders and group owner folders to remain distinguishable after the suffix is hidden, so that color and icon carry the owner kind signal together.
26. As an operator, I want unknown or blank owner strings handled safely, so that a missing value does not crash the dashboard.
27. As an administrator, I want group key upload affordances that currently hard-code `.g` in labels to follow the new presentation, so that admin key UI matches the rest of the product.
28. As an operator, I want the glossary concept of owner folder to remain the full `{identifier}.u` or `{identifier}.g` name, so that docs and operators still share one storage term.
29. As an operator, I want owner kind to remain encoded only in the owner folder suffix on disk, so that the UI does not invent a second source of truth.
30. As a developer, I want regression coverage on the display helper for user, group, scoped path, and malformed inputs, so that future UI refactors do not reintroduce raw suffixes in the wrong places.

## Implementation Decisions

- Treat this as a frontend presentation change only; do not alter owner folder storage, OpenAPI, or backend parsing.
- Introduce one shared owner-folder display helper as the single tested seam: parse owner folder or scoped path into owner kind (user, group, or unknown), bare identifier, and tooltip/full string.
- Render read-only owners through a small shared label component that consumes the helper and shows a Material Person or Group icon with a kind-specific tint, then the bare identifier.
- Add `@mui/icons-material` for Person and Group icons; keep icon size compact so table rows stay dense.
- Apply the label anywhere owners are shown for reading: zone catalog Owner column, rights Owner column, key picker owner mentions, missing-key scoped paths, and read-only confirmation/header prose that currently prints raw owner folders.
- Leave form inputs, placeholders, and submitted values as full owner folder strings (for example `admin.u`, `dns-team.g`).
- For scoped key paths, derive owner kind from the owner segment, show icon + bare owner + key basename, and put the exact scoped path in the tooltip.
- For strings that are not valid owner folders, show the raw string with no icon and the same string as tooltip.
- Do not invent owner kind from group membership or other context when the folder string itself is invalid.
- Keep domain language aligned with the glossary: owner folder remains the full folder name; owner kind is user versus group as encoded by the suffix.

## Testing Decisions

- Good tests exercise external behavior of the display helper: inputs and outputs for kind, bare name, and tooltip — not icon component internals or CSS.
- Preferred seam: the owner-folder display helper (pure functions). Ideal number of seams: one.
- Cover at least: user owner folder, group owner folder, scoped key path under each kind, malformed folder, and empty/blank handling.
- Do not require end-to-end browser tests for icon rendering unless the project already has that harness.
- Prior art: frontend has little automated UI coverage today; prefer a small unit/test module next to the helper if the frontend toolchain already supports it, otherwise document helper cases in a focused test file the project can run. Backend API tests are out of scope because the API does not change.

## Out of Scope

- Renaming owner folders on disk or changing the `.u` / `.g` encoding.
- Changing create/transfer/update APIs or validation of owner folders.
- Autocompleting or rewriting form inputs to hide suffixes while typing.
- Color themes beyond a simple user-versus-group icon tint.
- Accessibility overhaul beyond icon accessible names and tooltips.
- Reconciliation, rights derivation, or key file storage behavior.

## Further Notes

Grilled decisions: display-only everywhere (forms keep full strings); MUI Person/Group icons; icon tint by owner kind; scoped paths included; tooltip shows exact full string; malformed values stay raw. Glossary term **Owner kind** records user versus group as encoded by the owner folder suffix. No ADR — presentation-only and easy to reverse.
