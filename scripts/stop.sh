#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for name in backend frontend; do
  if [ -f "data/$name.pid" ]; then
    kill "$(cat "data/$name.pid")" 2>/dev/null || true
    rm -f "data/$name.pid"
  fi
done
pkill -f 'aurora-app-demo.*spring-boot:run' 2>/dev/null || true
pkill -f 'vite --port 5173' 2>/dev/null || true
docker compose -f docker/compose.yml stop
echo "stopped (middleware containers preserved; use 'docker compose -f docker/compose.yml down' to remove)"
