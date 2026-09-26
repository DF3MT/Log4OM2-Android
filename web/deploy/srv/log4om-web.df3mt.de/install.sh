#!/usr/bin/env bash
# Install / update web stack under /srv/log4om-web.df3mt.de
# Requires API stack up first (creates external network "log4om").
set -euo pipefail

DEST="${DEST:-/srv/log4om-web.df3mt.de}"
cd "$DEST"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env"
fi

if ! docker network inspect log4om >/dev/null 2>&1; then
  echo "ERROR: Docker network 'log4om' missing. Start /srv/log4om-api.df3mt.de first." >&2
  exit 1
fi

echo "==== pull (GHCR) ===="
docker compose pull
echo "==== up (no build) ===="
docker compose up -d --no-build
docker compose ps
