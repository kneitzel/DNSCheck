#!/usr/bin/env bash
# Feedback loop: expired / invalidated session is not handled as auth loss in the UI.
#
# Symptom (user-facing):
#   After the server no longer recognizes the session cookie, the SPA stays on the
#   authenticated shell and surfaces domain errors (missing-keys panel / Unauthorized)
#   instead of returning to the login screen. A full page reload clears React state
#   and shows login again.
#
# This script goes RED while that gap exists, GREEN once a central 401 handler exists
# and MissingKeysPanel no longer treats 401 as a missing-keys domain failure.
#
# Usage (server on :8080 optional for API checks; source checks always run):
#   bash scripts/repro-session-expiry-ui.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${DNSCHECK_BASE:-http://127.0.0.1:8080}"
APP="$ROOT/dnscheck-service/src/main/frontend/src/App.tsx"
MISSING="$ROOT/dnscheck-service/src/main/frontend/src/components/MissingKeysPanel.tsx"
RED=0

echo "=== 1) API: stale session cookie → 401 (empty body) ==="
if curl -sS -o /dev/null -w "%{http_code}" "$BASE/api/v1/health" | grep -qx 200; then
  STALE_JAR="$(mktemp)"
  trap 'rm -f "$STALE_JAR"' EXIT
  printf '# Netscape HTTP Cookie File\n127.0.0.1\tFALSE\t/\tFALSE\t0\tDNSCHECK_SESSION\tstale-session-id\n' > "$STALE_JAR"
  for ep in /api/v1/keys/missing /api/v1/zones /api/v1/status; do
    code=$(curl -sS -b "$STALE_JAR" -o /tmp/dnscheck-session-repro.json -w "%{http_code}" "$BASE$ep")
    echo "  $ep -> HTTP $code"
    if [ "$code" != "401" ]; then
      echo "FAIL: expected 401 for stale session on $ep, got $code"
      exit 1
    fi
  done
  echo "OK: backend rejects stale session with 401 (not missing-key / not 403)"
else
  echo "SKIP: server not reachable at $BASE (source checks still run)"
fi

echo
echo "=== 2) Frontend: MissingKeysPanel maps 401 to domain error (misleading) ==="
if grep -q 'status === 401' "$MISSING" \
  && grep -q 'Could not load missing key summary (not authenticated)' "$MISSING"; then
  echo "RED: MissingKeysPanel still special-cases 401 as a missing-keys panel error"
  RED=1
else
  echo "OK: MissingKeysPanel no longer treats 401 as a missing-keys domain failure"
fi

echo
echo "=== 3) Frontend: no central 401 → clear session / show login ==="
# Fixed shape: a shared helper or OpenAPI interceptor that clears auth on 401.
# Until then, only loadDashboard()'s broad catch clears loggedInUser — other
# call sites (MissingKeysPanel, zone view, mutations) keep the shell mounted.
if grep -Eq 'onUnauthorized|handleUnauthorized|sessionExpired|STATUS_CODE_UNAUTHORIZED|status === 401' "$APP"; then
  echo "OK: App.tsx references a central unauthorized/session-expiry path"
else
  echo "RED: App.tsx has no central 401 / session-expiry handler"
  RED=1
fi

echo
if [ "$RED" -eq 1 ]; then
  echo "VERDICT: RED — expired session is not caught as auth loss in the SPA"
  exit 1
fi
echo "VERDICT: GREEN — session expiry appears handled centrally"
exit 0
