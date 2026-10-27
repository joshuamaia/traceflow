#!/usr/bin/env bash
# Runs TraceFlow locally without Docker: backend with the embedded H2 database ("local" profile) and the Angular
# dev server with an /api proxy. Requires Java 25 and Node.js 22+ (Maven is provided by the wrapper).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Starting backend on http://localhost:8080 (profile: local, H2 database in backend/data/)"
(cd "$ROOT/backend" && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local) &
BACKEND_PID=$!
trap 'echo "==> Stopping backend"; kill $BACKEND_PID 2>/dev/null || true' EXIT

echo "==> Waiting for the backend to become healthy"
for _ in $(seq 1 90); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then break; fi
  sleep 2
done

echo "==> Starting frontend on http://localhost:4200"
cd "$ROOT/frontend"
[ -d node_modules ] || npm ci
npx ng serve --port 4200
