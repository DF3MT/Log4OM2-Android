#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

. /etc/os-release
CODENAME="$VERSION_CODENAME"
if ! curl -fsI "https://download.docker.com/linux/ubuntu/dists/${CODENAME}/stable/" >/dev/null 2>&1; then
  CODENAME=noble
fi

echo "Using Docker apt codename: $CODENAME"
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin docker-buildx-plugin

# Put Docker data on /srv (more free space than /)
mkdir -p /srv/docker
if [[ ! -f /etc/docker/daemon.json ]]; then
  cat > /etc/docker/daemon.json <<'EOF'
{
  "data-root": "/srv/docker"
}
EOF
  systemctl restart docker
fi

docker --version
docker compose version
echo DOCKER_INSTALL_OK
