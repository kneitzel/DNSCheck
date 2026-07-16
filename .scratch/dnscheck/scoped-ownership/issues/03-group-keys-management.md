# 03 — Group keys management

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Admins manage TSIG key files in `{groupname}.g/` folders from the group detail page; group members see those keys listed read-only but cannot download them.

**Blocked by:** [02 — Personal keys management](02-personal-keys-management.md)

**Status:** done

- [x] Admin group-scoped REST endpoints provide full key CRUD for `{groupname}.g/` (same file rules as personal keys)
- [x] Group members can list group keys via read-only access on group detail; download is rejected for members
- [x] Global admin has CRUD on all group key folders
- [x] Group detail page keys section: admin sees upload, edit, replace, download, delete; member sees list-only
- [x] Group member read access to group detail aligns with existing management pages pattern (role-gated UI)
- [x] REST Assured tests cover admin CRUD, member list-only, member download forbidden
- [x] `mvn verify` passes
