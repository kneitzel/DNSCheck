#!/usr/bin/env bash
# Feedback loop: dashboard "Could not load missing key summary."
#
# Proves:
#   - Authenticated + no missing keys → HTTP 200 {"missing":[]} (UI should hide panel)
#   - Unauthenticated → HTTP 401 (UI shows the error string)
#
# Usage (server running on :8080, admin/admin):
#   bash scripts/repro-missing-keys-summary.sh
set -euo pipefail

BASE="${DNSCHECK_BASE:-http://127.0.0.1:8080}"
USER="${DNSCHECK_USER:-admin}"
PASS="${DNSCHECK_PASS:-admin}"
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

echo "=== unauthenticated (expect 401 / UI error path) ==="
code=$(curl -sS -o /tmp/missing-unauth.json -w "%{http_code}" "$BASE/api/v1/keys/missing" || true)
echo "HTTP $code body=$(cat /tmp/missing-unauth.json 2>/dev/null || true)"
if [ "$code" != "401" ]; then
  echo "FAIL: expected 401 without session, got $code"
  exit 1
fi
echo "OK: unauthenticated failure matches UI error path"

echo "=== authenticated empty summary (expect 200 {"'"'"missing"'"'":[]}) ==="
curl -sS -c "$JAR" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  "$BASE/api/v1/auth/login" >/tmp/missing-login.json
body=$(curl -sS -b "$JAR" -w '\n%{http_code}' "$BASE/api/v1/keys/missing")
code=$(printf '%s\n' "$body" | tail -n1)
json=$(printf '%s\n' "$body" | sed '$d')
echo "HTTP $code body=$json"
if [ "$code" != "200" ]; then
  echo "FAIL: expected 200 with session, got $code"
  exit 1
fi
if [ "$json" != '{"missing":[]}' ]; then
  echo "NOTE: body is not an empty missing list (may be ok if keys are actually missing)"
fi
echo "OK: authenticated call succeeded (empty missing does NOT produce the UI error string)"
