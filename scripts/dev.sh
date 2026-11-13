#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
export JAVA_HOME
mkdir -p data/logs

echo "==> middleware"
docker compose -f docker/compose.yml up -d

echo "==> waiting for MySQL"
for i in $(seq 1 60); do
  if docker exec aurora-mysql mysqladmin ping -h localhost -uroot -paurora_root_pw >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

# Triggers are created by Flyway; MySQL 8 needs this when binary logging is on.
docker exec aurora-mysql mysql -uroot -paurora_root_pw -e "SET GLOBAL log_bin_trust_function_creators = 1" >/dev/null 2>&1 || true

echo "==> building backend"
mvn -q -Dmaven.test.skip=true install

echo "==> starting backend :8080"
(cd aurora-app-demo && nohup mvn -q spring-boot:run > "$ROOT/data/logs/backend.log" 2>&1 & echo $! > "$ROOT/data/backend.pid")

echo "==> starting frontend :5173"
(cd aurora-ui-shell && nohup pnpm dev > "$ROOT/data/logs/frontend.log" 2>&1 & echo $! > "$ROOT/data/frontend.pid")

for i in $(seq 1 60); do
  if curl -s http://localhost:8080/api/health >/dev/null 2>&1; then break; fi
  sleep 2
done
for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:5173/ >/dev/null 2>&1; then break; fi
  sleep 1
done

echo
echo "BaPi is up:"
echo "  UI      http://localhost:5173"
echo "  API     http://localhost:8080/api/health"
echo "  login   admin / admin123"
echo "  logs    data/logs/backend.log, data/logs/frontend.log"
