# 22 — Zone view layout and ad-hoc record edit

**What to build:** Make the zone records table fit on a laptop (drop Expected columns, overflow menu, scroll safety net), rename Modify to Edit, extend modify so ad-hoc records can change live rdata/TTL without joining the zone definition, and write-gate Add + row mutations.

**Status:** done

**Triage:** ready-for-agent

**Spec:** [20-zone-view-edit-layout-and-adhoc-modify.md](../specs/20-zone-view-edit-layout-and-adhoc-modify.md)

**Tickets:** [01](../zone-view-edit/issues/01-adhoc-live-edit.md) · [02](../zone-view-edit/issues/02-zone-view-layout-overflow-menu.md)

- [x] Zone records table: Name / Type / Status / Rdata / TTL / Actions; Expected columns removed; horizontal scroll safety net
- [x] Expected values in Edit dialog; Status chip hint for drifted/missing
- [x] Per-row ⋮ menu with status-appropriate actions; UI label Edit (not Modify)
- [x] Edit for ad-hoc, enforced, drifted only (not missing / marked_for_deletion)
- [x] PATCH records supports ad-hoc live-only modify; managed modify unchanged; empty ad-hoc TTL leaves live TTL
- [x] Add form and row mutations gated on zone write
- [x] OpenAPI description updated; operator guide updated
- [x] REST Assured covers ad-hoc modify, managed modify regression, write 403
- [x] `mvn verify` passes
