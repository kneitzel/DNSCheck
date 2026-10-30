# 01 — Frontend must treat session expiry as auth loss (not domain errors)

**Type:** Bug  
**Status:** ready-for-agent  
**Blocked by:** None — can start immediately  
**Feedback loop:** `bash scripts/repro-session-expiry-ui.sh` (currently RED)

## Summary

When the browser still has an authenticated SPA shell but the server no longer accepts the `DNSCHECK_SESSION` cookie (cookie max-age elapsed, service restart clearing the in-memory session map, or logout/invalidation with a stale client cookie), API calls return **HTTP 401 with an empty body**. The React app does **not** centrally clear `loggedInUser` / show `LoginPanel`. Individual panels instead surface misleading domain errors (especially the Missing keys panel). A full page reload drops React state, shows login, and works again after sign-in.

## User-visible symptom

1. Operator works in the UI while authenticated.
2. Session becomes invalid on the server (or cookie stops being sent) without a full reload.
3. UI stays in the authenticated `AppShell`.
4. Dashboard / zone actions show errors that look like data or rights problems, e.g.:
   - Missing keys: `Could not load missing key summary (not authenticated). Sign in again and refresh.`
   - Zone view / other calls: `Unauthorized` (from the generated client’s 401 mapping)
   - Mutations: generic “Check permissions…” copy (easy to read as access denied)
5. Reload → login screen → sign in → everything works.

This is **not** a missing TSIG key on disk (`MissingKeyFileException` → HTTP **409** with `Referenced key file is missing: …`). Backend auth failure is consistently **401**.

## Repro

### Automated (agent-runnable)

```bash
bash scripts/repro-session-expiry-ui.sh
```

Expect **exit 1 (RED)** until fixed. The script checks:

1. Stale `DNSCHECK_SESSION` → `401` on `/api/v1/keys/missing`, `/zones`, `/status` (backend OK).
2. `MissingKeysPanel` still special-cases `401` as a missing-keys domain error.
3. `App.tsx` has no central unauthorized / session-expiry handler.

### Manual

1. Sign in via the UI.
2. Invalidate the server session while keeping the SPA open, e.g. restart the Quarkus service (in-memory `SessionService` is cleared; browser cookie remains until max-age), **or** wait until the cookie’s 8h `Max-Age` elapses without reloading.
3. Trigger dashboard / missing-keys / zone view / a mutation.
4. Observe authenticated shell + misleading errors; reload shows login.

## Root cause (diagnosis)

### What “session expiry” means today

| Layer | Behavior |
| --- | --- |
| Cookie | Set on login with `HttpOnly`, path `/`, **`Max-Age` = 8 hours** (`AuthResourceImpl.sessionMaxAgeSeconds`). |
| Server session | `SessionService` is an **in-memory** `ConcurrentHashMap` with **no TTL**. Entries disappear on process restart or `invalidate` (logout). |
| Auth failure | `SessionAuthMechanism` returns no identity → Quarkus → **401**, empty body. |

So “expired” in practice = cookie gone **or** cookie present but unknown to the server. Backend behavior is correct; the bug is frontend handling.

### Why the SPA misleads

- Auth gate is only `if (!loggedInUser) return <LoginPanel />`. `loggedInUser` is React state, set on login and cleared mainly by:
  - `logout()`
  - `loadDashboard()`’s **broad** `catch` (any failure → `setLoggedInUser(null)`)
- Most other call sites catch errors locally and never clear auth:
  - `MissingKeysPanel` maps `401` to a missing-keys alert
  - `loadZoneView` sets `zoneViewError` via `formatApiError` → `"Unauthorized"`
  - create/update/delete zone show permission-flavored strings
- `MissingKeysPanel` mounts once when the shell is shown; after expiry it can paint an auth failure as a **Missing keys** problem even though keys are fine.
- Reload clears `loggedInUser` (`null` initial state). `loadDashboard` is not run until after login, so the operator must sign in again — matching the reported recovery path.

## Ranked hypotheses (for implementers)

1. **No global 401 handler (primary)** — If a single OpenAPI/`fetch` interceptor (or shared `handleApiError`) cleared session + showed login on `401`, the misleading panels would not stay mounted. Prediction: adding that handler makes the repro script GREEN and removes the need for per-panel 401 copy.
2. **MissingKeysPanel local 401 mapping** — Amplifies the symptom into “missing key” language. Prediction: removing the special case alone reduces confusion but leaves Unauthorized / permissions noise elsewhere.
3. **Cookie max-age without server TTL** — Cookie can outlive a restarted process; SPA keeps state until an API fails. Prediction: aligning server TTL with cookie max-age does not fix the UI bug by itself; still need client handling.
4. **`loadDashboard` catch is too coarse / unused on most paths** — Refresh accidentally “fixes” by logging out; other actions do not. Prediction: relying on that catch is insufficient; 401 must be handled at the transport layer.
5. **403 vs 401 confusion (lower)** — True rights failures are 403/`Forbidden`. Unlikely for pure expiry; do not collapse 403 into forced logout.

## What to build

End-to-end: when any authenticated API call returns **401**, the UI must treat the operator as logged out — clear session UI state, show `LoginPanel` (optional short “Session expired, please sign in again” message), and **not** show missing-keys / zone / permission domain errors for that failure.

Suggested approach (implementer choice, keep seams deep):

1. **Centralize** 401 handling next to `OpenAPI.WITH_CREDENTIALS` (generated client hook, thin wrapper, or small auth session module used by `App`).
2. On 401: `setLoggedInUser(null)`, clear dashboard/zone state (same as logout cleanup), set a dedicated auth message.
3. Stop treating 401 as a missing-keys domain error in `MissingKeysPanel` (either rely on unmount after logout, or rethrow / ignore once global handler runs).
4. Do **not** auto-logout on **403** (real access denial).
5. Optional follow-up (separate acceptance if desired): server-side session TTL matching cookie `Max-Age`, and/or sliding refresh — not required to fix the misleading UI.

## Acceptance criteria

- [ ] `bash scripts/repro-session-expiry-ui.sh` exits **0 (GREEN)**
- [ ] After session invalidation with SPA still open, the next API `401` returns the operator to the login screen (no Missing keys / zone-view domain error for that failure)
- [ ] Valid session behavior unchanged (login, dashboard, missing-keys empty list, zone view)
- [ ] HTTP **403** still surfaces as permission/forbidden UI where applicable — not forced logout
- [ ] Regression coverage: frontend unit/integration test and/or keep the shell script as the gate; optional REST Assured already covers 401 on protected routes
- [ ] `mvn verify` / frontend `npm test` / frontend build still pass

## Out of scope

- Changing TSIG / missing-key-on-disk semantics (`409` + `MissingKeyFileException`)
- Persisting sessions across restarts (unless explicitly added as a follow-up)
- MFA / login challenge changes

## Evidence captured (2026-07-20)

```text
=== STALE cookie ===
/api/v1/keys/missing -> 401 body=
/api/v1/zones -> 401 body=
/api/v1/status -> 401 body=

bash scripts/repro-session-expiry-ui.sh
VERDICT: RED — expired session is not caught as auth loss in the SPA
```

Relevant code:

- `AuthResourceImpl` — cookie `Max-Age` 8h
- `SessionService` — in-memory sessions, no TTL
- `App.tsx` — `loggedInUser` gate; `loadDashboard` catch clears auth; no global 401 path
- `MissingKeysPanel.tsx` — explicit `401` → missing-keys error string
- Generated client — empty-body `401` → `ApiError` message `"Unauthorized"`
