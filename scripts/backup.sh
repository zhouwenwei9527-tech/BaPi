#!/usr/bin/env bash
#
# backup.sh — 备份演示环境的全部数据（MySQL + pgvector + MinIO）
#
# 用法: ./scripts/backup.sh [输出目录，默认 backup]
# 产物:
#   backup/aurora-mysql-<时间>.sql.gz   业务库（用户/角色/审批/审计/会话）
#   backup/aurora-vector-<时间>.sql.gz  向量库（文档 + 向量切片）
#   backup/aurora-minio-<时间>.tar.gz   对象存储（上传的文件与壁纸）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-${ROOT}/backup}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "${OUT}"

echo "==> 备份 MySQL (aurora)"
docker exec aurora-mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers aurora' \
  | gzip > "${OUT}/aurora-mysql-${STAMP}.sql.gz"

echo "==> 备份 pgvector (aurora_vector)"
docker exec aurora-pgvector sh -c \
  'exec pg_dump -U aurora -d aurora_vector' \
  | gzip > "${OUT}/aurora-vector-${STAMP}.sql.gz"

echo "==> 备份 MinIO 对象存储"
docker run --rm --volumes-from aurora-minio alpine \
  tar czf - -C / data \
  > "${OUT}/aurora-minio-${STAMP}.tar.gz"

echo
ls -lh "${OUT}" | grep "${STAMP}"
echo "==> 完成：共 3 个文件"
