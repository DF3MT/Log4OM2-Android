#!/usr/bin/env bash
# Install / update Log4OM stack under /srv/log4om on this host.
set -euo pipefail

DEST=/srv/log4om
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root" >&2
  exit 1
fi

mkdir -p "$DEST/nginx"
cp -f "$SCRIPT_DIR/docker-compose.yml" "$DEST/docker-compose.yml"
cp -f "$SCRIPT_DIR/nginx/log4om.conf" "$DEST/nginx/log4om.conf"

if [[ ! -f "$DEST/.env" ]]; then
  cp -f "$SCRIPT_DIR/.env.example" "$DEST/.env"
  # Generate secrets on first install
  DB_PW="$(openssl rand -base64 24 | tr -d '\n=/+' | cut -c1-24)"
  JWT="$(openssl rand -base64 48 | tr -d '\n=/+' | cut -c1-48)"
  ENC="$(openssl rand -hex 16)"
  sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${DB_PW}/" "$DEST/.env"
  sed -i "s/^JWT_SECRET=.*/JWT_SECRET=${JWT}/" "$DEST/.env"
  sed -i "s/^ENCRYPTION_KEY=.*/ENCRYPTION_KEY=${ENC}/" "$DEST/.env"
  echo "Created $DEST/.env with generated secrets"
else
  echo "Keeping existing $DEST/.env"
fi

cd "$DEST"
# Images come from GHCR (GitHub Actions). Never build on the host.
docker compose pull
docker compose up -d --no-build

echo
echo "Stack is up. Point DNS log4om.df3.mt.de → this host and open:"
echo "  http://log4om.df3.mt.de/"
echo "API health (via nginx): http://log4om.df3.mt.de/api/actuator/health"
docker compose ps
