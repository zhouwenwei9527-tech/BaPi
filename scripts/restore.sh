#!/usr/bin/env bash
#
# restore.sh — 从 scripts/backup.sh 的产物恢复数据
#
# 用法: ./scripts/restore.sh <mysql.sql.gz> <vector.sql.gz> <minio.tar.gz>
# 说明:
#   - 恢复会覆盖现有数据，请先确认中间件容器在运行
#   - MySQL 恢复前会重建库；pgvector 恢复前会重建库；MinIO 会清空对象卷
set -euo pipefail

[ $# -eq 3 ] || { echo "用法: $0 <mysql.sql.gz> <vector.sql.gz> <minio.tar.gz>"; exit 1; }
MYSQL_DUMP="$1"; VECTOR_DUMP="$2"; MINIO_TAR="$3"
for f in "${MYSQL_DUMP}" "${VECTOR_DUMP}" "${MINIO_TAR}"; do
  [ -f "$f" ] || { echo "缺少 $f"; exit 1; }
done

echo "==> 恢复 MySQL (aurora)"
docker exec aurora-mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SET GLOBAL log_bin_trust_function_creators=1; DROP DATABASE IF EXISTS aurora; CREATE DATABASE aurora CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"'
gunzip -c "${MYSQL_DUMP}" | docker exec -i aurora-mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --binary-mode aurora'

echo "==> 恢复 pgvector (aurora_vector)"
docker exec aurora-pgvector psql -U aurora -d postgres -q \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'aurora_vector' AND pid <> pg_backend_pid()" \
  -c "DROP DATABASE IF EXISTS aurora_vector" \
  -c "CREATE DATABASE aurora_vector OWNER aurora"
gunzip -c "${VECTOR_DUMP}" | docker exec -i aurora-pgvector psql -U aurora -d aurora_vector -v ON_ERROR_STOP=1 -q

echo "==> 恢复 MinIO 对象存储"
docker run --rm --volumes-from aurora-minio -v "$(cd "$(dirname "${MINIO_TAR}")" && pwd)":/backup:ro alpine \
  sh -c "rm -rf /data/* && tar xzf '/backup/$(basename "${MINIO_TAR}")' -C /"

echo "==> 完成"
