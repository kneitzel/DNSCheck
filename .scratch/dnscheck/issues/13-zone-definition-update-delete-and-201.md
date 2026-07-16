# 13 — Zone definition update, delete, and create 201

**What to build:** Operators with `zone-create` or `admin` update nameserver and key file references, delete zone definition JSON from the catalog, and see create return HTTP 201. React catalog gains edit and delete dialogs.

**Blocked by:** 09 — Backend prefactor: rights helpers and catalog bootstrap

**Status:** done

**Spec:** [10-zone-definition-update-delete.md](../specs/10-zone-definition-update-delete.md)

- [x] OpenAPI defines `PATCH` and `DELETE` on `/api/v1/zones/{zoneName}`
- [x] Patch updates nameserver and/or key file; delete removes JSON only (no live DNS changes)
- [x] `POST /api/v1/zones` returns HTTP 201 Created per OpenAPI
- [x] Readers and writers without `zone-create` get 403 on patch and delete
- [x] React zone catalog: edit and delete with confirmation; list refreshes after changes
- [x] REST Assured tests cover patch, delete, 201, and rights denial
- [x] `mvn verify` passes
