# 21 — Scoped ownership for keys and zones

**What to build:** Scoped `{username}.u` / `{groupname}.g` folders for keys and zone catalog; filesystem-authoritative zone ownership with computed rights; key CRUD API and UI; zone API routes `/api/v1/zones/{owner}/{zoneName}`; transfer, selectable keys, missing-key dashboard; CLI `owner/zoneName` targeting.

**Status:** done

**Spec:** [19-scoped-ownership-keys-and-zones.md](../specs/19-scoped-ownership-keys-and-zones.md)

**Triage:** ready-for-agent

**Tickets:** [01](../scoped-ownership/issues/01-foundation-owner-folder-model.md) · [02](../scoped-ownership/issues/02-personal-keys-management.md) · [03](../scoped-ownership/issues/03-group-keys-management.md) · [04](../scoped-ownership/issues/04-scoped-zone-catalog-and-derived-rights.md) · [05](../scoped-ownership/issues/05-supplemental-zone-grants.md) · [06](../scoped-ownership/issues/06-zone-ownership-transfer.md) · [07](../scoped-ownership/issues/07-zone-key-picker-missing-keys-in-use.md) · [08](../scoped-ownership/issues/08-migration-documentation.md)

- [x] Core: scoped zone catalog scan and catalog entry identity
- [x] Core: key store with scoped paths, validation, in-use / missing-key scans
- [x] Service: effective rights resolver; zone grant `ownerFolder`; OpenAPI + REST for zones, keys, transfer, selectable keys, missing keys
- [x] Service: REST Assured tests (primary seam)
- [x] Frontend: `/account/keys`, group detail keys, zone owner + key picker, dashboard missing keys, route updates for `{owner}/{zoneName}`
- [x] CLI: reconcile `owner/zoneName`
- [x] Docs: manual migration for flat keys and zones
- [x] `mvn verify` passes
