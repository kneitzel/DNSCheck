# DNSCheck

An infrastructure tool that verifies DNS records against a known desired state and reconciles drift by updating BIND zones via `nsupdate`. Protects records that other infrastructure tools (e.g. Stalwart) may remove or overwrite. Operators manage zone definitions and managed records via an authenticated API.

## Language

### Security

**Multi-factor authentication**:
An optional operator sign-in safeguard that requires a password plus a second verification factor before a session is created. In DNSCheck, this is the canonical term; "2FA" is a shorthand.
_Avoid_: 2FA, second-factor login

**MFA enrollment**:
The operator's pending setup state while a second verification factor is being added and confirmed. Enrollment becomes active only after verification succeeds.
_Avoid_: Setup flow, activation

**Recovery code**:
A one-time backup code used to regain access when the second verification factor is unavailable.
_Avoid_: Backup code, emergency code

**MFA reset**:
An admin action that clears an operator's MFA enrollment so the operator can sign in with password only and re-enroll.
_Avoid_: Disable MFA, force reset

**DNSCheck**:
The reconciliation tool itself — checks live DNS against expected records and repairs mismatches. Delivered as a core library, a Quarkus service with UI, and a CLI for direct operator use.
_Avoid_: Monitor, sync agent, DNS bot

**Expected record**:
A single DNS record that should exist for a zone — type, name, TTL, and rdata defined as the source of truth.
_Avoid_: Desired record, target record, spec entry

**Drift**:
When live DNS differs from the expected state: a record is missing, has wrong rdata, or an unexpected extra record exists.
_Avoid_: Mismatch, desync, divergence

**Reconciliation**:
The act of bringing live DNS back in line with expected records, performed via BIND `nsupdate`. Covers three operations: adding missing records, correcting wrong rdata or TTL on managed records, and removing records marked for deletion. All changes for a zone are applied in a single `nsupdate` session.
_Avoid_: Sync, fix, repair (use only as verbs in prose, not as the domain noun)

**Managed record**:
An entry in a zone definition that DNSCheck actively enforces — present in live DNS with matching rdata, or added/corrected if missing or wrong. TTL enforcement follows the managed record's TTL policy. Record names may be written as FQDN or relative to the zone; DNSCheck normalizes all names to FQDN before comparison.
_Avoid_: Protected record, watched record, enforced entry

**Enforcement**:
Whether a record is tracked in the zone definition and actively reconciled. Enforced records are written to the zone definition JSON and updated in the live zone; non-enforced records are applied to the live zone only.
_Avoid_: Persist, save, track

**Ad-hoc record**:
A record created in the live zone via the API without being added to the zone definition. Not protected by reconciliation; DNSCheck will not repair or remove it unless a deletion marker matches.
_Avoid_: Temporary record, one-off record, unmanaged record

**Deletion marker**:
A managed record entry with a delete flag. If a live record matches the entry's name, type, and optional rdata glob pattern, DNSCheck removes it via `nsupdate`. Records not listed in the zone definition are never touched.
_Avoid_: Tombstone, blacklist entry, remove directive

**Rdata pattern**:
A glob expression (`*`, `?`) matched against a record's rdata. Omitted means all rdata values for the given name and type match.
_Avoid_: Regex, wildcard filter

**Reconciliation run**:
One complete pass: load zone definitions, AXFR each live zone, compare, and apply `nsupdate` changes for all drift found. Each zone is reconciled independently — a failure in one zone does not prevent others from being processed. Triggered by the service schedule, the service API, or the CLI tool.
_Avoid_: Sync cycle, check run, pass

**Zone outcome**:
The result of reconciling a single zone within a run: success (no drift), reconciled (drift corrected), failed (error prevented completion), or drift detected (dry-run mode — changes identified but not applied).
_Avoid_: Zone result, zone status, per-zone report

**Dry-run**:
A mode where DNSCheck performs AXFR and drift detection but does not send `nsupdate` commands. Reports what would change without modifying live DNS. Controlled by a service-level default with an optional per-run override.
_Avoid_: Check-only, preview mode, simulate

**Schedule**:
The configured interval at which DNSCheck automatically performs a reconciliation run. Defaults to 15 minutes; configurable per deployment. Picks up manual JSON file changes on the next run.
_Avoid_: Cron, timer, job

**Zone catalog**:
The set of zone definitions DNSCheck manages, discovered by scanning scoped owner folders under a configured location. Each zone definition lives in exactly one owner folder.
_Avoid_: Zones directory, config folder, zone list

**Owner folder**:
A scoped namespace under the zone catalog or keys directory named `{username}.u` for an operator or `{groupname}.g` for a group. Ownership of zone definitions and key files is determined by which owner folder contains them.
_Avoid_: Scope directory, tenant folder, namespace

**Zone owner**:
The operator or group that owns a zone definition, identified by the owner folder it is stored in. Zone access rights are derived from zone ownership and group membership.
_Avoid_: Zone holder, catalog owner, file owner

**Zone catalog entry**:
One zone definition file in the catalog, identified by its owner folder and zone name (e.g. `admin.u` and `example.com`). The same DNS zone name may appear in multiple catalog entries under different owners; each entry is reconciled independently.
_Avoid_: Zone ID, zone path, zone record

**TTL policy**:
How a managed record's optional `ttl` field is interpreted: omitted means TTL is ignored during comparison and correction; a positive value is enforced; the sentinel value `-1` means use the zone's default TTL.
_Avoid_: TTL setting, ttl mode

**Zone default TTL**:
The TTL DNSCheck applies when a managed record's TTL policy is "use default", derived from the live zone's SOA record minimum field retrieved via AXFR.
_Avoid_: Domain TTL, $TTL, default expiry

**Zone**:
A DNS zone managed by BIND that DNSCheck is authorized to query and update.
_Avoid_: Domain (too broad — a domain may span multiple zones)

**Zone definition**:
A JSON file that fully describes one DNS zone: the zone name (must match the filename), expected records, the nameserver to contact, and the TSIG key for authenticated AXFR and `nsupdate`. Stored under an owner folder in the zone catalog; separate from operator data in the database.
_Avoid_: Zone config, zone file, zone spec

**TSIG key**:
Shared secret credentials used to authenticate zone transfers (AXFR) and dynamic updates (`nsupdate`) against BIND per RFC 2136. Key metadata (name, algorithm) and secret material are stored in an external key file; the zone definition references that file by path.
_Avoid_: API key, auth token, credentials

**Key file**:
An external file in BIND native key syntax holding TSIG key name, algorithm, and secret for a zone. Stored under an owner folder in the keys directory and referenced from the zone definition by scoped path relative to that directory.
_Avoid_: Credentials file, secret file, tsig.conf

**Live zone**:
The current record set as held by the authoritative nameserver, retrieved via AXFR.
_Avoid_: Current DNS, actual DNS, resolved records

**Operator**:
An authenticated user who manages zone definitions and managed records through the DNSCheck API. Operator accounts, groups, and rights are stored in the application database. Authentication uses server-side sessions. The first admin is bootstrapped via environment variables in production; a default admin account exists in development only.
_Avoid_: User, admin, account

**Global right**:
A permission that applies across all zones in the catalog. Two global rights exist: `admin` (manage operators, groups, and all zone rights) and `zone-create` (create new zone definitions).
_Avoid_: Global permission, system role, admin right

**Zone right**:
A permission scoped to a single zone catalog entry. Two zone rights exist: `read` (view zone view and reconciliation outcomes) and `write` (manage records, promote, demote, delete, and trigger reconcile for that entry). Rights are derived from zone ownership and group membership; supplemental grants extend access beyond what ownership provides.
_Avoid_: Zone permission, local role, scoped access

**Supplemental zone grant**:
An explicit zone right assigned to an operator beyond what is derived from zone ownership. Identified by owner folder and zone name. Used to grant access to non-owners on personal zones or to add rights outside the default ownership rules.
_Avoid_: Extra grant, delegated access, zone permission override

**Zone management**:
Creating, modifying, or removing zone definitions and their managed records via the API, subject to the operator's rights. Includes promoting ad-hoc records to enforced and demoting enforced records.
_Avoid_: Zone CRUD, zone editing, configuration management

**Promotion**:
Adding an existing live zone record to the zone definition, making it enforced. Typically initiated from a zone view showing record status.
_Avoid_: Enforce, upgrade, adopt

**Demotion**:
Removing a record from the zone definition without deleting it from the live zone. The record becomes ad-hoc and is no longer reconciled; it remains in live DNS.
_Avoid_: Un-enforce, deactivate, release

**Deletion**:
Removing a record from the live zone via `nsupdate`. If the record is also present in the zone definition, it is removed from the JSON as well.
_Avoid_: Delete, remove, purge

**Record status**:
The state of a record in the zone view: enforced (in JSON, matches live), ad-hoc (live only), drifted (in JSON, live differs), missing (in JSON, absent from live), or marked for deletion.
_Avoid_: Record state, health, sync status

**Zone view**:
A merged picture of a zone's live records and managed records, with each record's status, used by operators and UI to decide on enforce, demote, modify, or delete actions.
_Avoid_: Zone snapshot, zone dashboard, zone report
