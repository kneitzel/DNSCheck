# 02 — Owner display on remaining read-only surfaces

**What to build:** The same owner-folder presentation (icon + bare name, tooltip with exact string; scoped paths as icon + bare owner + key basename) is used on the other read-only surfaces: rights Owner column, key picker owner mentions, missing-key scoped paths, and read-only confirmation/header prose that currently prints raw owner folders or scoped paths.

**Blocked by:** 01 — Owner display on zone catalog

**Status:** ready-for-agent

- [ ] Rights summary Owner column uses the shared owner display
- [ ] Key picker owner mentions use the shared owner display (not hard-coded `.u` / `.g` labels)
- [ ] Missing-key scoped paths use icon + bare owner + key basename with full-path tooltip
- [ ] Read-only confirmation/header prose that showed raw owner folders uses the shared display
- [ ] Display helper covers scoped key path inputs (user and group)
- [ ] Form inputs and submitted values remain full owner folder / scoped path strings
