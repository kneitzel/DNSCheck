# 08 — Migration documentation

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Operators and admins can manually migrate from flat keys and zone layouts to scoped `{username}.u` / `{groupname}.g` folders using documented steps — no automated migration tool.

**Blocked by:** [07 — Zone key picker, missing keys, and in-use enforcement](07-zone-key-picker-missing-keys-in-use.md)

**Status:** done

- [x] Configure/operator docs describe scoped layout for keys and zone catalog directories
- [x] Manual migration steps for moving flat key files and zone JSON into owner folders and updating `keyFile` paths and supplemental grants
- [x] CLI documentation updated for `dnscheck reconcile {owner}/{zoneName}` syntax
- [x] Docs note flat root files are ignored after migration and optional warnings in logs
- [x] README links to new or updated sections where appropriate
