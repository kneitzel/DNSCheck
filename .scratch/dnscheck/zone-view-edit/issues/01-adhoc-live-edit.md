# 01 — Ad-hoc live Edit (API + UI action)

**Parent:** [22 — Zone view layout and ad-hoc record edit](../../issues/22-zone-view-edit-layout-and-adhoc-modify.md) · [Spec 20](../../specs/20-zone-view-edit-layout-and-adhoc-modify.md)

**What to build:** Operators with write can Edit an ad-hoc record’s rdata and optional TTL on live DNS only (empty TTL leaves live TTL unchanged). Enforced and drifted Edit keep existing JSON-plus-live modify behavior. OpenAPI describes both cases; REST Assured covers ad-hoc modify, managed modify regression, and write 403. Zone view exposes Edit for ad-hoc / enforced / drifted (menu layout may still be the existing button row until ticket 02).

**Blocked by:** None — can start immediately

**Status:** done

- [x] `PATCH …/records` updates ad-hoc live rdata/TTL without writing the zone definition; status stays ad-hoc
- [x] Empty / omitted `newTtl` on ad-hoc leaves live TTL unchanged; positive `newTtl` applies to live only
- [x] Managed (enforced/drifted) modify still updates zone definition and live DNS
- [x] Edit UI action available for ad-hoc, enforced, and drifted (not missing / marked_for_deletion)
- [x] OpenAPI summary/description covers ad-hoc and managed modify
- [x] REST Assured: ad-hoc modify, managed modify regression, write 403
- [x] `mvn verify` passes
