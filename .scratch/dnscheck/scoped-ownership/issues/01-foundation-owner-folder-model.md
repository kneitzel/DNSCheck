# 01 — Foundation: owner folder model and zone grant schema expand

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Shared domain primitives for scoped ownership — catalog entry identity, owner-folder naming rules, and database schema expand for supplemental zone grants — so keys and zones tickets can proceed in parallel without redefining conventions.

**Blocked by:** None — can start immediately

**Status:** done

- [x] Core introduces catalog entry identity (`ownerFolder` + `zoneName`) and owner-folder validation (`{identifier}.u` / `{identifier}.g`, safe identifier rules)
- [x] Zone grant entity extended with `owner_folder` column (nullable acceptable during expand; required for new supplemental grants in later tickets)
- [x] Shared helpers resolve scoped paths under configured keys and zones roots without path traversal
- [x] Unit tests cover identity parsing, folder name validation, and path resolution edge cases
- [x] `mvn verify` passes with no user-facing behavior change yet
