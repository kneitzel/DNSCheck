# 14 — Deletion markers (API + zone view UI)

**What to build:** Operators with `write` on a zone add and remove deletion markers in zone definition JSON via the API and zone view. Markers persist to JSON only; reconciliation applies removals. Live records matching a marker show `marked_for_deletion`.

**Blocked by:** 10 — Frontend prefactor: vocabulary and component split

**Status:** done

**Spec:** [11-deletion-markers-api.md](../specs/11-deletion-markers-api.md)

- [x] OpenAPI defines POST and DELETE deletion-marker endpoints under a zone
- [x] Marker create/remove requires `write` on the zone (or `admin`)
- [x] Optional rdata glob pattern supported; empty pattern matches all rdata at name
- [x] Zone view: "Mark for deletion" and "Remove marker" actions; no immediate nsupdate
- [x] REST Assured tests: marker in JSON, view status, reader denied
- [x] `mvn verify` passes
