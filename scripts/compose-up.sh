#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

mysql_image="${MYSQL_IMAGE:-docker.m.daocloud.io/library/mysql:8.0}"
redis_image="${REDIS_IMAGE:-docker.m.daocloud.io/library/redis:7-alpine}"
qdrant_image="${QDRANT_IMAGE:-docker.m.daocloud.io/qdrant/qdrant:v1.12.4}"
minio_image="${MINIO_IMAGE:-docker.m.daocloud.io/minio/minio:RELEASE.2024-10-13T13-34-11Z}"

echo "拉取镜像（不走 BuildKit solve）..."
docker pull "$mysql_image"
docker pull "$redis_image"
docker pull "$qdrant_image"
docker pull "$minio_image"

if command -v docker-compose >/dev/null 2>&1; then
  echo "使用 docker-compose 启动..."
  docker-compose up -d
else
  echo "未找到 docker-compose，回退 docker compose（若报 dockerfile.v0 请安装 docker-compose v1）..."
  docker compose up -d
fi

docker ps --filter name=lumi-
