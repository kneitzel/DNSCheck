# Optional enforcement for record management

When an operator creates or changes a record via the API, they choose whether to enforce it:

- **Enforced** — record is written to the zone definition JSON and applied to the live zone via `nsupdate`. DNSCheck reconciles it on every run.
- **Non-enforced (ad-hoc)** — record is applied to the live zone only; not added to JSON. DNSCheck does not protect it unless later promoted.

Operators can promote ad-hoc records to enforced (copied from live zone into JSON) or demote enforced records (removed from JSON, kept in live DNS). Deletion always removes from live DNS and from JSON if present.

The zone view merges live zone (AXFR) and zone definition data, showing per-record status (enforced, ad-hoc, drifted, missing, marked for deletion) to drive the UI.
