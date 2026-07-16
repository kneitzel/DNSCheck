# 02 — Personal keys management

**Parent:** [21 — Scoped ownership for keys and zones](../../issues/21-scoped-ownership-keys-and-zones.md)

**What to build:** Operators manage TSIG key files in their own `{username}.u/` folder through the REST API and an Account keys page — upload, raw BIND edit, replace, download, and delete — without shell access. Global admin cannot see or manage another operator's personal keys.

**Blocked by:** [01 — Foundation: owner folder model and zone grant schema expand](01-foundation-owner-folder-model.md)

**Status:** done

- [x] Account-scoped REST endpoints list, upload, read content, update content (BIND validated), replace upload, download, and delete personal keys
- [x] Upload uses safe basename rules (`[a-zA-Z0-9._-]+`, must end with `.key`); duplicate basename in same folder returns 409
- [x] Owner folder `{username}.u/` created lazily on first upload
- [x] Only the owning operator (and not global admin) can access personal key endpoints for that folder
- [x] Frontend `/account/keys` page supports upload, BIND textarea edit, replace, download, and delete
- [x] REST Assured tests cover happy path, 409 duplicate, 403 for non-owner and admin on another operator's keys
- [x] `mvn verify` passes
