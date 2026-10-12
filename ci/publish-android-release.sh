#!/usr/bin/env bash
# Publish a signed release APK to the GitLab generic package registry.
# The phone downloads .../packages/generic/log4om-android/latest/release.json
# with no token. See .gitlab-ci.yml for the required CI variables.
set -euo pipefail

missing=0
for v in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "Missing CI/CD variable: $v"
    echo "Create it under Settings → CI/CD → Variables (same values as the GitHub Actions secrets)."
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  echo "Refusing to build an unsigned release. The in-app updater only installs APKs signed with the existing key."
  exit 1
fi

apt-get update -qq
apt-get install -y -qq curl unzip ca-certificates jq >/dev/null

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$CI_PROJECT_DIR/.android-sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl --fail --silent --show-error --location \
    --output /tmp/cmdline-tools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  rm -rf /tmp/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools
  mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  chmod +x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
fi
mkdir -p "$ANDROID_HOME/licenses"
printf '%s\n' 24333f8a63b6825ea9c5514f83c2829b004d1fee > "$ANDROID_HOME/licenses/android-sdk-license"
printf '%s\n' 84831b9409646161da1a6b8083a0d5c5 > "$ANDROID_HOME/licenses/android-sdk-preview-license"
timeout 120 yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

printf '%s' "$KEYSTORE_BASE64" | tr -d '\r\n ' | base64 -d > "$CI_PROJECT_DIR/release.keystore"
test -s "$CI_PROJECT_DIR/release.keystore"
trap 'rm -f "$CI_PROJECT_DIR/release.keystore"' EXIT

PACKAGE_NAME="${PACKAGE_NAME:-log4om-android}"
PROJECT_ENC=${CI_PROJECT_PATH//\//%2F}
if [ "$PROJECT_ENC" != "df3mt%2FLog4OM2-Android" ]; then
  echo "App update URL is hardcoded for df3mt/Log4OM2-Android, this project is ${CI_PROJECT_PATH}."
  exit 1
fi
PACKAGE_BASE="${CI_API_V4_URL}/projects/${PROJECT_ENC}/packages/generic/${PACKAGE_NAME}"

GH_CODE=$(
  curl --fail --silent --show-error --max-time 30 \
    -H "Accept: application/vnd.github+json" \
    -H "User-Agent: Log4OM-CI" \
    https://api.github.com/repos/DF3MT/Log4OM2-Android/releases/latest \
    | jq -r '.tag_name // empty' | sed -n 's/^build-//p' || true
)
if ! [[ "${GH_CODE:-}" =~ ^[0-9]+$ ]]; then
  echo "Could not read the latest GitHub build-N; falling back to 10."
  GH_CODE=10
fi

GL_CODE=0
page=1
while [ "$page" -le 10 ]; do
  pkg_json=$(curl --silent --show-error --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
    "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages?package_type=generic&package_name=${PACKAGE_NAME}&per_page=100&page=${page}")
  if printf '%s' "$pkg_json" | jq -e 'type=="object" and has("message")' >/dev/null; then
    echo "Package list failed: $pkg_json"
    exit 1
  fi
  count=$(printf '%s' "$pkg_json" | jq 'if type=="array" then length else 0 end')
  [ "$count" -eq 0 ] && break
  page_max=$(printf '%s' "$pkg_json" | jq '[.[] | .version | capture("^build-(?<n>[0-9]+)$") | .n | tonumber] | max // 0')
  if [ "$page_max" -gt "$GL_CODE" ]; then GL_CODE=$page_max; fi
  [ "$count" -lt 100 ] && break
  page=$((page + 1))
done

if [ -n "${VERSION_CODE:-}" ]; then
  CODE="$VERSION_CODE"
else
  if [ "$GH_CODE" -gt "$GL_CODE" ]; then BASE=$GH_CODE; else BASE=$GL_CODE; fi
  CODE=$((BASE + 1))
fi
if ! [[ "$CODE" =~ ^[0-9]+$ ]]; then
  echo "VERSION_CODE must be an integer, got '${CODE}'."
  exit 1
fi
NAME="1.0.${CODE}"
TAG="build-${CODE}"
APK_NAME="Log4OM-Android-${NAME}.apk"
VERSIONED_BASE="${PACKAGE_BASE}/${TAG}"
LATEST_BASE="${PACKAGE_BASE}/latest"
echo "Publishing ${TAG} (${NAME}), GitHub latest build-${GH_CODE}, GitLab packages up to build-${GL_CODE}."

sed -i 's/\r$//' gradlew
chmod +x gradlew
export KEYSTORE_FILE="$CI_PROJECT_DIR/release.keystore"
export VERSION_CODE="$CODE"
export VERSION_NAME="$NAME"
./gradlew :app:assembleRelease --no-daemon --stacktrace

mkdir -p dist
SRC=$(find app/build/outputs/apk/release -name "*.apk" -print -quit)
test -n "$SRC"
cp "$SRC" "dist/${APK_NAME}"
cp "$SRC" dist/Log4OM-Android-latest.apk


# shellcheck source=gitlab-package-lib.sh
source "$(dirname "$0")/gitlab-package-lib.sh"
publish_android_package
