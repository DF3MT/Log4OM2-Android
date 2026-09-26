#!/usr/bin/env bash
# Install / update API stack under /srv/log4om-api.df3mt.de
set -euo pipefail

DEST="${DEST:-/srv/log4om-api.df3mt.de}"
cd "$DEST"

if [[ ! -f .env ]]; then
  cp .env.example .env
  DB_PW=$(openssl rand -base64 24 | tr -d '\n=/+' | cut -c1-24)
  JWT=$(openssl rand -base64 48 | tr -d '\n=/+' | cut -c1-48)
  ENC=$(openssl rand -hex 16)
  sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${DB_PW}/" .env
  sed -i "s/^JWT_SECRET=.*/JWT_SECRET=${JWT}/" .env
  sed -i "s/^ENCRYPTION_KEY=.*/ENCRYPTION_KEY=${ENC}/" .env
  echo "Created .env with generated secrets"
fi

grep -q '^CORS_ORIGIN=' .env && sed -i 's/^CORS_ORIGIN=.*/CORS_ORIGIN=*/' .env || echo 'CORS_ORIGIN=*' >> .env

echo "==== pull (GHCR) ===="
docker compose pull
echo "==== up (no build) ===="
docker compose up -d --no-build
docker compose ps
