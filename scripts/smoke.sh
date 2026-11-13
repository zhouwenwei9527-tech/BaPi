#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')
echo "login: ok (${TOKEN:0:10}...)"
curl -s "$BASE/api/health" | python3 -c 'import sys,json; d=json.load(sys.stdin)["data"]; print("health:", d["status"], "| vector:", d["vectorStore"], "| storage:", d["storage"])'
curl -s "$BASE/api/search?q=%E6%95%B0%E6%8D%AE%E8%8C%83%E5%9B%B4&limit=5" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin)["data"]; print("search:", len(d["results"]), "results | trimmed", d["trimmed"], "| chunks", d["vectorChunks"])'
curl -s "$BASE/api/audit/verify" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin)["data"]; print("audit:", d["message"])'
curl -s "$BASE/api/plugins" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin)["data"]; print("plugins:", [(p["pluginId"], p["status"]) for p in d])'
