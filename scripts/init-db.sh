#!/usr/bin/env bash
# 在 compose 的 PostgreSQL 中按文件名序执行全部迁移脚本，初始化或升级数据库 schema。
# 用法：./scripts/init-db.sh（需已执行 ./scripts/dev-start.sh 启动容器）
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE_FILE="resources/docker/compose.yml"

docker compose -f "$COMPOSE_FILE" up -d postgres

echo "等待 PostgreSQL 就绪..."
until docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U lingxi >/dev/null 2>&1; do
  sleep 1
done

for sql_file in resources/database/postgresql/migration/*.sql; do
  echo "应用迁移: $sql_file"
  docker compose -f "$COMPOSE_FILE" exec -T postgres psql -v ON_ERROR_STOP=1 -U lingxi -d lingxi < "$sql_file"
done

echo "数据库初始化完成。"
