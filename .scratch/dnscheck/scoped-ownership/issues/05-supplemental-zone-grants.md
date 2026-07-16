# 05 — Supplemental zone grants by owner and zone name

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Admins assign explicit zone grants keyed by `(ownerFolder, zoneName)` so collaborators can access personal zones they do not own. Derived ownership rights from ticket 04 remain; supplemental grants extend access on top.

**Blocked by:** [04 — Scoped zone catalog and derived rights](04-scoped-zone-catalog-and-derived-rights.md)

**Status:** done

- [x] Zone grant persistence requires `owner_folder` + `zone_name` for supplemental grants
- [x] Effective rights resolver applies supplemental grants after derived ownership rules
- [x] Admin grant API and UI create/revoke grants using owner folder and zone name (not DNS zone name alone)
- [x] Operator with supplemental read can view zone; supplemental write can manage records and reconcile
- [x] REST Assured tests: collaborator granted read on another operator's personal zone; grant scoped to owner+zone does not apply to duplicate DNS name under different owner
- [x] `mvn verify` passes
