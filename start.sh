#!/usr/bin/env bash
#
# start.sh — BaPi 本地一键启动（Docker 中间件 + 后端 Spring Boot + 前端 Vite）
#
# 用法:
#   ./start.sh          # 启动全部（已在运行的服务自动跳过）
#   ./start.sh stop     # 停止本地服务（后端按端口/PID 停止，前端 dev server 杀进程）
#   ./start.sh status   # 查看各服务状态
#
# 说明:
#   - 中间件: MySQL 3308 / pgvector 5433 / MinIO 9000+9001（docker/compose.yml）
#   - 后端  : aurora-app-demo，端口 8080，日志 logs/backend.log
#   - 前端  : aurora-ui-shell Vite dev，端口 5173，日志 logs/frontend.log
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${REPO_ROOT}/data/logs"
PID_DIR="${REPO_ROOT}/data"
mkdir -p "${LOG_DIR}"

BACKEND_PORT=8080
FRONTEND_PORT=5173
CONTAINERS=(aurora-mysql aurora-pgvector aurora-minio)

C_GREEN='\033[32m'; C_RED='\033[31m'; C_YEL='\033[33m'; C_END='\033[0m'
ok()   { echo -e "  ${C_GREEN}✓${C_END} $*"; }
fail() { echo -e "  ${C_RED}✗${C_END} $*"; }
warn() { echo -e "  ${C_YEL}!${C_END} $*"; }

port_up() { curl -s -o /dev/null --max-time 2 "http://127.0.0.1:$1${2:-}"; }
backend_up() { curl -s --max-time 2 http://127.0.0.1:${BACKEND_PORT}/api/health | grep -q '"status":"UP"'; }

# ---------- status ----------
do_status() {
  echo "== BaPi 本地环境状态 =="
  docker info >/dev/null 2>&1 \
    && ok "Docker daemon" || fail "Docker daemon 未运行（open -a Docker）"
  for c in "${CONTAINERS[@]}"; do
    docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$c" && ok "容器 $c" || fail "容器 $c 未运行"
  done
  backend_up && ok "后端      http://127.0.0.1:${BACKEND_PORT}/api/health" \
    || fail "后端 :${BACKEND_PORT}"
  port_up ${FRONTEND_PORT} && ok "前端      http://127.0.0.1:${FRONTEND_PORT}" \
    || fail "前端 :${FRONTEND_PORT}"
}

# ---------- stop ----------
do_stop() {
  echo "== 停止 BaPi 本地服务 =="

  # 前端 dev server（按 PID 文件，退而求其次按端口）
  if [ -f "${PID_DIR}/frontend.pid" ]; then
    kill "$(cat "${PID_DIR}/frontend.pid")" 2>/dev/null && ok "前端 dev server 已停止" \
      || warn "前端 PID 已失效，尝试按端口停止"
    rm -f "${PID_DIR}/frontend.pid"
  fi
  local fe_pid
  fe_pid=$(lsof -nP -iTCP:${FRONTEND_PORT} -sTCP:LISTEN -t 2>/dev/null | head -1)
  if [ -n "${fe_pid}" ]; then
    kill "${fe_pid}" 2>/dev/null && ok "前端 :${FRONTEND_PORT} 已停止" || warn "前端停止失败"
  fi

  # 后端（先停 java 监听进程，再停 mvn 包装进程）
  local be_pid
  be_pid=$(lsof -nP -iTCP:${BACKEND_PORT} -sTCP:LISTEN -t 2>/dev/null | head -1)
  if [ -n "${be_pid}" ]; then
    kill "${be_pid}" 2>/dev/null && ok "后端 :${BACKEND_PORT} 已停止" || warn "后端 java 停止失败"
  fi
  if [ -f "${PID_DIR}/backend.pid" ]; then
    local mvn_pid
    mvn_pid=$(cat "${PID_DIR}/backend.pid")
    pkill -P "${mvn_pid}" 2>/dev/null || true
    kill "${mvn_pid}" 2>/dev/null || true
    rm -f "${PID_DIR}/backend.pid"
  fi
  [ -z "${be_pid}" ] && [ ! -f "${PID_DIR}/backend.pid" ] && warn "后端未在运行"

  # 中间件保留运行（数据都在容器卷里，不必每次重启）
  warn "中间件容器保持运行（如需停止: docker compose -f docker/compose.yml stop）"
}

# ---------- start ----------
do_start() {
  echo "== BaPi 本地环境启动 =="

  # 1. Docker daemon + 中间件容器
  if ! docker info >/dev/null 2>&1; then
    warn "Docker daemon 未运行，正在启动 Docker Desktop..."
    open -a Docker
    until docker info >/dev/null 2>&1; do sleep 2; done
  fi
  ok "Docker daemon"
  docker compose -f "${REPO_ROOT}/docker/compose.yml" up -d >/dev/null 2>&1
  local need_start=""
  for c in "${CONTAINERS[@]}"; do
    docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$c" || need_start="$need_start $c"
  done
  if [ -n "${need_start}" ]; then
    warn "等待中间件就绪${need_start} ..."
    for i in $(seq 1 60); do
      local all_up=1
      for c in "${CONTAINERS[@]}"; do
        docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$c" || all_up=0
      done
      [ "$all_up" = 1 ] && break
      sleep 2
    done
  fi
  local all_up=1
  for c in "${CONTAINERS[@]}"; do
    docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$c" || all_up=0
  done
  [ "$all_up" = 1 ] && ok "中间件 MySQL/pgvector/MinIO 运行中" \
    || { fail "中间件未全部就绪，查: docker compose -f docker/compose.yml ps"; exit 1; }

  # 审计触发器依赖的 MySQL 全局变量（幂等）
  docker exec aurora-mysql mysql -uroot -paurora_root_pw \
    -e "SET GLOBAL log_bin_trust_function_creators = 1" >/dev/null 2>&1 || true

  # 2. 后端 :8080（已在运行自动跳过）
  if backend_up; then
    ok "后端已在运行 :${BACKEND_PORT}（跳过）"
  else
    if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
      JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
      export JAVA_HOME
    fi
    warn "启动后端 Spring Boot（首次需编译，1~2 分钟无输出属正常，请勿 Ctrl-C）..."
    ( cd "${REPO_ROOT}/aurora-app-demo" \
      && nohup mvn -q spring-boot:run > "${LOG_DIR}/backend.log" 2>&1 \
      & echo $! > "${PID_DIR}/backend.pid" )
    for i in $(seq 1 90); do backend_up && break; sleep 2; done
    backend_up && ok "后端      http://127.0.0.1:${BACKEND_PORT}/api/health (日志: logs/backend.log)" \
      || { fail "后端启动失败，查 logs/backend.log"; exit 1; }
  fi

  # 3. 前端 :5173
  if port_up ${FRONTEND_PORT}; then
    ok "前端已在运行 :${FRONTEND_PORT}（跳过）"
  else
    warn "启动前端 dev server..."
    ( cd "${REPO_ROOT}/aurora-ui-shell" \
      && nohup pnpm dev > "${LOG_DIR}/frontend.log" 2>&1 \
      & echo $! > "${PID_DIR}/frontend.pid" )
    for i in $(seq 1 60); do port_up ${FRONTEND_PORT} && break; sleep 2; done
    port_up ${FRONTEND_PORT} && ok "前端      http://127.0.0.1:${FRONTEND_PORT} (日志: logs/frontend.log)" \
      || { fail "前端启动失败，查 logs/frontend.log"; exit 1; }
  fi

  echo ""
  ok "全部就绪: 中间件 3308/5433/9000 · 后端 :${BACKEND_PORT} · 前端 :${FRONTEND_PORT}"
  echo -e "  ${C_YEL}登录${C_END} admin/admin123 · auditor/audit123（独立复核人）"
}

case "${1:-start}" in
  start)  do_start ;;
  stop)   do_stop ;;
  status) do_status ;;
  *) echo "用法: ./start.sh [start|stop|status]"; exit 1 ;;
esac
