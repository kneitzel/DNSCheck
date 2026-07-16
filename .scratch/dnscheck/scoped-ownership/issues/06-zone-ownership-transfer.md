# 06 — Zone ownership transfer

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Operators and admins can move a zone definition between owner folders via the API. Personal zones: owner or admin. Group zones: admin only. Supplemental grants re-key to the new owner folder.

**Blocked by:** [04 — Scoped zone catalog and derived rights](04-scoped-zone-catalog-and-derived-rights.md), [05 — Supplemental zone grants by owner and zone name](05-supplemental-zone-grants.md)

**Status:** done

- [x] Transfer endpoint moves zone JSON from `{oldOwner}/{zoneName}.json` to `{newOwner}/{zoneName}.json`; rejects if target exists
- [x] Personal zone: owning operator or global admin may transfer; group zone: global admin only
- [x] Supplemental grants for `(oldOwner, zoneName)` re-keyed to `(newOwner, zoneName)` automatically
- [x] Derived rights after transfer follow new owner folder (no stale DB rows for derived access)
- [x] REST Assured tests: owner transfer personal to group; admin transfer group zone; member cannot transfer group zone; grants follow transfer
- [x] `mvn verify` passes
