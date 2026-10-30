#!/usr/bin/env bash
# Feedback loop: edit dialog invents owner/zone.key → save → zone view reports missing key.
# Exit 1 (RED) while the bug exists; exit 0 (GREEN) once catalog exposes keyFile and
# the UI would no longer invent a wrong path (asserted here via catalog + optional PATCH).
#
# Usage: ./scripts/repro-edit-invents-missing-key.sh [baseUrl]
set -euo pipefail

BASE="${1:-http://127.0.0.1:8080}"
JAR="$(mktemp)"
ZONE_FILE="${ZONE_FILE:-$HOME/bin/DNSCheck/zones/admin.u/neitzel.de.json}"
OWNER=admin.u
ZONE=neitzel.de
trap 'rm -f "$JAR"' EXIT

curl -sS -c "$JAR" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' "$BASE/api/v1/auth/login" >/dev/null

ENTRY="$(curl -sS -b "$JAR" "$BASE/api/v1/zones" | python3 -c "
import json,sys
entries=json.load(sys.stdin).get('entries') or []
e=next((x for x in entries if x.get('zone')=='$ZONE' and x.get('ownerFolder')=='$OWNER'), None)
if not e:
    sys.exit('zone not in catalog')
print(json.dumps(e))
")"

KEY_FROM_CATALOG="$(python3 -c "import json,sys; e=json.loads(sys.argv[1]); print(e.get('keyFile') or '')" "$ENTRY")"
INVENTED="${OWNER}/${ZONE}.key"

if [[ -z "$KEY_FROM_CATALOG" ]]; then
  echo "RED: catalog entry has no keyFile; UI openEditZone invents $INVENTED"
  if [[ ! -f "$ZONE_FILE" ]]; then
    echo "Skip PATCH corrupt step (ZONE_FILE missing: $ZONE_FILE)"
    exit 1
  fi
  cp "$ZONE_FILE" "${ZONE_FILE}.repro.bak"
  curl -sS -b "$JAR" -o /tmp/repro-upd.json -w 'PATCH %{http_code}\n' \
    -X PATCH -H 'Content-Type: application/json' \
    -d "{\"nameserver\":\"ns1.neitzel.de\",\"keyFile\":\"$INVENTED\"}" \
    "$BASE/api/v1/zones/$OWNER/$ZONE"
  VIEW_CODE="$(curl -sS -b "$JAR" -o /tmp/repro-view.json -w '%{http_code}' \
    "$BASE/api/v1/zones/$OWNER/$ZONE/view")"
  echo "VIEW HTTP $VIEW_CODE body=$(cat /tmp/repro-view.json)"
  cp "${ZONE_FILE}.repro.bak" "$ZONE_FILE"
  rm -f "${ZONE_FILE}.repro.bak"
  if [[ "$VIEW_CODE" == "409" ]] && grep -q 'Referenced key file is missing' /tmp/repro-view.json; then
    echo "Confirmed: invented keyFile causes missing-key on zone view."
    exit 1
  fi
  echo "Unexpected: view did not report missing key after invent+save."
  exit 2
fi

if [[ "$KEY_FROM_CATALOG" == "$INVENTED" ]]; then
  echo "RED: catalog keyFile equals invented path ($INVENTED) — still wrong"
  exit 1
fi

echo "GREEN: catalog keyFile=$KEY_FROM_CATALOG (not invented $INVENTED)"
exit 0
