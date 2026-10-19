#!/usr/bin/env bash
# End-to-end check of a running VaultPay (docker compose in CI, or a deployed URL):
#   scripts/smoke-test.sh [base-url] [staff-password]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
STAFF_PASSWORD="${2:-local-staff-password}"
EMAIL="smoke-$(date +%s)-$RANDOM@example.com"
PASSWORD="smoke-test-password-123"

echo "Waiting for $BASE/actuator/health"
for _ in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health" | grep -q '"UP"'; then break; fi
  sleep 3
done
curl -fsS "$BASE/actuator/health" | grep -q '"UP"'

echo "Dashboard is served"
curl -fsS "$BASE/" | grep -q '<div id="root">'

json() { curl -fsS -H 'Content-Type: application/json' "$@"; }

echo "Register, log in, open two accounts"
json -X POST "$BASE/api/auth/register" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Smoke Test\"}" > /dev/null
TOKEN=$(json -X POST "$BASE/api/auth/login" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"
A=$(json -H "$AUTH" -X POST "$BASE/api/accounts" -d '{"name":"Current","currency":"GBP"}' | jq -r .id)
B=$(json -H "$AUTH" -X POST "$BASE/api/accounts" -d '{"name":"Savings","currency":"GBP"}' | jq -r .id)

echo "Deposit and transfer"
json -H "$AUTH" -H "Idempotency-Key: smoke-dep-$RANDOM$RANDOM" -X POST "$BASE/api/accounts/$A/deposits" \
  -d '{"amountMinor":10000,"reference":"Smoke deposit"}' > /dev/null
json -H "$AUTH" -H "Idempotency-Key: smoke-xfer-$RANDOM$RANDOM" -X POST "$BASE/api/transfers" \
  -d "{\"fromAccountId\":\"$A\",\"toAccountId\":\"$B\",\"amountMinor\":2500,\"reference\":\"Smoke transfer\"}" > /dev/null

BALANCE=$(json -H "$AUTH" "$BASE/api/accounts/$A" | jq -r .balanceMinor)
[ "$BALANCE" = "7500" ] || { echo "Unexpected balance $BALANCE"; exit 1; }
COUNT=$(json -H "$AUTH" "$BASE/api/accounts/$A/transactions" | jq '.items | length')
[ "$COUNT" = "2" ] || { echo "Unexpected history length $COUNT"; exit 1; }

echo "Admin panel reports a balanced ledger"
ADMIN=$(json -X POST "$BASE/api/auth/login" -d "{\"email\":\"admin@vaultpay.local\",\"password\":\"$STAFF_PASSWORD\"}" | jq -r .accessToken)
BALANCED=$(json -H "Authorization: Bearer $ADMIN" "$BASE/api/admin/overview" | jq -r .ledgerBalanced)
[ "$BALANCED" = "true" ] || { echo "Ledger not balanced"; exit 1; }

echo "Smoke test passed"
