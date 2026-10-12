#!/usr/bin/env bash
# Copy the GitHub Release APK for this commit into the GitLab generic package.
# Used when KEYSTORE_* is not configured yet, so the switching build (signed by
# GitHub Actions) is still installable from GitLab. No token is embedded in the app.
set -euo pipefail

apt-get update -qq
apt-get install -y -qq curl ca-certificates jq >/dev/null

REPO="DF3MT/Log4OM2-Android"
API="https://api.github.com/repos/${REPO}"
UA="Log4OM-CI"
checked=" "
found=""

for attempt in $(seq 1 24); do
  releases=$(curl --fail --silent --show-error --max-time 30 \
    -H "Accept: application/vnd.github+json" \
    -H "User-Agent: ${UA}" \
    "${API}/releases?per_page=5")
  tags=$(printf '%s' "$releases" | jq -r '.[].tag_name')
  for tag in $tags; do
    case "$checked" in
      *" ${tag} "*) continue ;;
    esac
    sha=$(curl --fail --silent --show-error --max-time 30 \
      -H "Accept: application/vnd.github+json" \
      -H "User-Agent: ${UA}" \
      "${API}/commits/${tag}" | jq -r '.sha // empty')
    checked="${checked}${tag} "
    echo "GitHub ${tag} → ${sha}"
    if [ "$sha" = "$CI_COMMIT_SHA" ]; then
      found="$tag"
      break
    fi
  done
  if [ -n "$found" ]; then
    break
  fi
  echo "Waiting for the GitHub release of ${CI_COMMIT_SHA} (${attempt}/24)."
  sleep 30
done

if [ -z "$found" ]; then
  echo "No GitHub release for ${CI_COMMIT_SHA} after 12 minutes."
  exit 1
fi

if ! [[ "$found" =~ ^build-([0-9]+)$ ]]; then
  echo "Unexpected GitHub tag '${found}'."
  exit 1
fi
CODE="${BASH_REMATCH[1]}"
NAME="1.0.${CODE}"
TAG="$found"
APK_NAME="Log4OM-Android-${NAME}.apk"
echo "Mirroring GitHub ${TAG} into the GitLab package registry."

release_json=$(curl --fail --silent --show-error --max-time 30 \
  -H "Accept: application/vnd.github+json" \
  -H "User-Agent: ${UA}" \
  "${API}/releases/tags/${TAG}")
mkdir -p dist
download_asset() {
  local asset_name="$1"
  local dest="$2"
  local url
  url=$(printf '%s' "$release_json" | jq -r --arg n "$asset_name" '.assets[] | select(.name==$n) | .browser_download_url')
  if [ -z "$url" ] || [ "$url" = "null" ]; then
    return 1
  fi
  echo "Downloading ${asset_name}"
  curl --fail --silent --show-error --location --max-time 180 \
    -H "User-Agent: ${UA}" \
    -H "Accept: application/octet-stream" \
    --output "$dest" \
    "$url"
  test -s "$dest"
}

download_asset "Log4OM-Android-latest.apk" dist/Log4OM-Android-latest.apk
if ! download_asset "$APK_NAME" "dist/${APK_NAME}"; then
  cp dist/Log4OM-Android-latest.apk "dist/${APK_NAME}"
fi

# shellcheck source=gitlab-package-lib.sh
source "$(dirname "$0")/gitlab-package-lib.sh"
publish_android_package
