#!/usr/bin/env bash
# Pull a GitLab registry image onto the production LXC and recreate that service.
# Does not print .env, does not start Watchtower, and does not recreate Postgres.
#
# Required CI/CD variables: DEPLOY_SSH_PRIVATE_KEY, DEPLOY_KNOWN_HOSTS
# Optional: REGISTRY_USER, REGISTRY_PASSWORD (otherwise the job token is used)
set -euo pipefail

target="${1:-}"
case "$target" in
  api)
    stack="${API_STACK_DIR:-/srv/log4om-api.df3mt.de}"
    service="api"
    image="${CI_REGISTRY_IMAGE}/api:${CI_COMMIT_SHORT_SHA}"
    health_url="http://127.0.0.1:8080/actuator/health"
    ;;
  web)
    stack="${WEB_STACK_DIR:-/srv/log4om-web.df3mt.de}"
    service="web"
    image="${CI_REGISTRY_IMAGE}/web:${CI_COMMIT_SHORT_SHA}"
    health_url="http://127.0.0.1:8081/api/health"
    ;;
  *)
    echo "Usage: deploy-stack.sh api|web"
    exit 1
    ;;
esac

missing=0
for v in DEPLOY_SSH_PRIVATE_KEY DEPLOY_KNOWN_HOSTS CI_REGISTRY CI_REGISTRY_IMAGE; do
  if [ -z "${!v:-}" ]; then
    echo "Missing CI/CD variable: $v"
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  echo "Set DEPLOY_SSH_PRIVATE_KEY to the private key for ${DEPLOY_USER:-root}@${DEPLOY_HOST:-192.168.96.191}."
  echo "Set DEPLOY_KNOWN_HOSTS to the output of: ssh-keyscan -t ed25519 ${DEPLOY_HOST:-192.168.96.191}"
  echo "Registry login uses the job token unless REGISTRY_USER and REGISTRY_PASSWORD are set."
  exit 1
fi

reg_user="${REGISTRY_USER:-$CI_REGISTRY_USER}"
reg_pass="${REGISTRY_PASSWORD:-$CI_REGISTRY_PASSWORD}"
if [ -z "$reg_user" ] || [ -z "$reg_pass" ]; then
  echo "Missing registry login. Set REGISTRY_USER and REGISTRY_PASSWORD, or leave them empty to use the job token."
  exit 1
fi

install -d -m 700 "${HOME}/.ssh"
printf '%s\n' "$DEPLOY_SSH_PRIVATE_KEY" | tr -d '\r' > "${HOME}/.ssh/id_deploy"
chmod 600 "${HOME}/.ssh/id_deploy"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "${HOME}/.ssh/known_hosts"
chmod 644 "${HOME}/.ssh/known_hosts"

pass_b64=$(printf '%s' "$reg_pass" | base64 | tr -d '\n')
unset reg_pass

ssh -i "${HOME}/.ssh/id_deploy" -o IdentitiesOnly=yes -o BatchMode=yes \
  "${DEPLOY_USER}@${DEPLOY_HOST}" \
  bash -s -- "$pass_b64" "$stack" "$service" "$image" "$CI_REGISTRY" "$reg_user" "$health_url" <<'EOF'
set -euo pipefail
reg_pass=$(printf '%s' "$1" | base64 -d)
stack="$2"
service="$3"
image="$4"
registry="$5"
reg_user="$6"
health_url="$7"
unset pass_b64 || true

if [ ! -f "${stack}/docker-compose.yml" ]; then
  echo "Compose file not found: ${stack}/docker-compose.yml"
  exit 1
fi

cfg=$(mktemp -d)
printf '%s' "$reg_pass" | docker --config "$cfg" login -u "$reg_user" --password-stdin "$registry"
unset reg_pass
DOCKER_CONFIG="$cfg" docker pull "$image"
rm -rf "$cfg"

tmp="${stack}/docker-compose.yml.tmp"
awk -v svc="$service" -v img="$image" '
  $0 ~ "^  " svc ":" { hit = 1 }
  hit && $0 ~ "^    image:" {
    print "    image: " img
    hit = 0
    next
  }
  { print }
' "${stack}/docker-compose.yml" > "$tmp"
grep -F "image: ${image}" "$tmp" >/dev/null
mv "$tmp" "${stack}/docker-compose.yml"
docker compose --project-directory "$stack" up -d --no-build --no-deps --force-recreate "$service"

ok=0
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18; do
  if curl -fsS "$health_url" | grep -q UP; then
    ok=1
    break
  fi
  sleep 5
done
if [ "$ok" -ne 1 ]; then
  echo "Health check failed: ${health_url}"
  exit 1
fi
echo "Deployed ${service} (${image})"
EOF
