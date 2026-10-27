#!/usr/bin/env bash
# Runs the backend (JUnit 5 + Mockito, H2) and frontend (Vitest) test suites and the production frontend build.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Backend tests"
(cd "$ROOT/backend" && ./mvnw -B test)

echo "==> Frontend tests"
cd "$ROOT/frontend"
[ -d node_modules ] || npm ci
npx ng test --watch=false

echo "==> Frontend production build"
npx ng build

echo "All tests passed."
