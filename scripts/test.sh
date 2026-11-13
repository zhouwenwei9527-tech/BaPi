#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
export JAVA_HOME

echo "==> backend acceptance tests"
mvn -q -pl aurora-app-demo test

echo "==> frontend unit tests"
(cd aurora-ui-shell && pnpm test)

echo "==> frontend build"
(cd aurora-ui-shell && pnpm build)

if curl -s http://localhost:8080/api/health >/dev/null 2>&1 && curl -s http://127.0.0.1:5173/ >/dev/null 2>&1; then
  echo "==> Playwright E2E"
  (cd aurora-ui-shell && pnpm e2e)
else
  echo "==> E2E skipped (start ./scripts/dev.sh first)"
fi
