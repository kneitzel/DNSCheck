# 10 — Frontend prefactor: vocabulary and component split

**What to build:** Replace "Purge" with "Delete record" in the zone view, and split the monolithic React app into feature panels (login, catalog, zone view, admin, reconcile dashboard) without changing routes or API usage.

**Blocked by:** None — can start immediately

**Status:** done

**Spec:** [14-standards-cleanup.md](../specs/14-standards-cleanup.md)

- [x] Record removal action uses domain-appropriate label ("Delete record")
- [x] `App.tsx` delegates to feature components; layout and state wiring only in root
- [x] Quinoa frontend build succeeds; existing UI flows unchanged aside from label
- [x] `mvn verify` passes
