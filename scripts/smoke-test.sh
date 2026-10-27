#!/usr/bin/env bash
# Smoke test against a running TraceFlow (default: the Docker stack on http://localhost:4200).
# Executes the five demo scenarios, imports the sample files and checks the main endpoints.
set -euo pipefail
BASE="${1:-http://localhost:4200}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }
json() { node -e "const d=JSON.parse(require('fs').readFileSync(0));console.log($1)"; }

echo "==> Health"
curl -fsS "$BASE/actuator/health" | grep -q '"UP"' || fail "backend not healthy"
curl -fsS -o /dev/null "$BASE/" || fail "frontend not reachable"

echo "==> Demo scenarios"
for entry in successful-request:200 service-timeout:504 database-exception:500 circuit-breaker:503 async-messaging:202; do
  key="${entry%%:*}"; expected="${entry##*:}"
  run=$(curl -fsS -X POST "$BASE/api/scenarios/$key/execute")
  status=$(echo "$run" | json 'd.status + " " + d.httpStatus')
  [ "$status" = "COMPLETED $expected" ] || fail "$key returned '$status'"
  trace=$(echo "$run" | json 'd.traceRef')
  curl -fsS "$BASE/api/traces/$trace" | json 'd.nodes.length' >/dev/null || fail "$key trace not queryable"
  echo "    $key -> HTTP $expected, trace $trace"
done

echo "==> Import of the distributed samples"
id=$(curl -fsS -F "files=@$ROOT/samples/distributed/api-gateway.log" -F "files=@$ROOT/samples/distributed/order-service.log" \
  -F "files=@$ROOT/samples/distributed/payment-service.jsonl" "$BASE/api/imports" | json 'd.id')
for _ in $(seq 1 60); do
  job=$(curl -fsS "$BASE/api/analyses/$id/job")
  [ "$(echo "$job" | json 'd.finished')" = "true" ] && break
  sleep 1
done
echo "    $(echo "$job" | json 'd.status + ": " + d.eventsCount + " events, " + d.tracesCount + " traces, " + d.parseErrorsCount + " parse problems"')"
[ "$(echo "$job" | json 'd.status')" = "COMPLETED" ] || fail "import failed"

echo "==> Read APIs"
curl -fsS "$BASE/api/dashboard" | json '"    dashboard: " + d.totalEvents + " events, " + d.errorEvents + " errors"'
curl -fsS "$BASE/api/errors/groups" | json '"    error groups: " + d.totalElements'
curl -fsS "$BASE/api/analytics" | json '"    analytics hasData=" + d.hasData'
curl -fsS -o /dev/null "$BASE/v3/api-docs" || fail "OpenAPI not reachable"

echo "Smoke test passed."
