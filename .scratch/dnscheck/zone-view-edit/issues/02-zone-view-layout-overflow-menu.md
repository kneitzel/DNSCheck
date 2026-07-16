# 02 — Zone view layout and overflow menu

**Parent:** [22 — Zone view layout and ad-hoc record edit](../../issues/22-zone-view-edit-layout-and-adhoc-modify.md) · [Spec 20](../../specs/20-zone-view-edit-layout-and-adhoc-modify.md)

**What to build:** The zone records table fits a laptop screen. Expected rdata/TTL columns are removed (values appear in the Edit dialog and as a Status chip hint for drifted/missing). Horizontal scroll is a safety net. All row mutations live in a per-row overflow menu; Modify is labeled Edit. Add record form and row mutations are gated on zone write. Operator guide matches the new UI.

**Blocked by:** [01 — Ad-hoc live Edit (API + UI action)](01-adhoc-live-edit.md)

**Status:** done

- [x] Table columns: Name, Type, Status, Rdata, TTL, Actions; Expected columns removed; horizontal scroll available
- [x] Expected values shown in Edit dialog; Status chip hint for drifted/missing
- [x] Per-row ⋮ menu with status-appropriate actions (Edit, Promote, Demote, Mark for deletion, Delete, Remove, Remove marker)
- [x] UI label is Edit (not Modify)
- [x] Add form and mutating menu items gated on zone write; read-only still sees table + Refresh
- [x] Operator guide updated for Edit, layout, and write gating
- [x] `mvn verify` passes
