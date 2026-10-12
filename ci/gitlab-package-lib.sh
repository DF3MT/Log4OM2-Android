#!/usr/bin/env bash
# Shared publish step. Caller sets TAG, CODE, NAME, APK_NAME and has already
# placed dist/${APK_NAME}, dist/Log4OM-Android-latest.apk in the workspace.
# Requires curl, jq, CI_JOB_TOKEN, and the standard GitLab CI variables.

publish_android_package() {
  local PROJECT_ENC PACKAGE_BASE VERSIONED_BASE LATEST_BASE
  PACKAGE_NAME="${PACKAGE_NAME:-log4om-android}"
  PROJECT_ENC=${CI_PROJECT_PATH//\//%2F}
  if [ "$PROJECT_ENC" != "df3mt%2FLog4OM2-Android" ]; then
    echo "App update URL is hardcoded for df3mt/Log4OM2-Android, this project is ${CI_PROJECT_PATH}."
    exit 1
  fi
  PACKAGE_BASE="${CI_API_V4_URL}/projects/${PROJECT_ENC}/packages/generic/${PACKAGE_NAME}"
  VERSIONED_BASE="${PACKAGE_BASE}/${TAG}"
  LATEST_BASE="${PACKAGE_BASE}/latest"

  jq -n \
    --arg tag "$TAG" \
    --arg name "Log4OM Android ${NAME}" \
    --arg body "Automated release from main (pipeline ${CI_PIPELINE_ID})."$'\n\n'"- Version: ${NAME} (${CODE})"$'\n'"- Commit: ${CI_COMMIT_SHA}" \
    --arg html "${CI_PROJECT_URL}/-/releases/${TAG}" \
    --arg latest "${VERSIONED_BASE}/Log4OM-Android-latest.apk" \
    --arg versioned "${VERSIONED_BASE}/${APK_NAME}" \
    --arg apk "$APK_NAME" \
    '{
      tag_name: $tag,
      name: $name,
      body: $body,
      html_url: $html,
      assets: [
        {name: "Log4OM-Android-latest.apk", browser_download_url: $latest},
        {name: $apk, browser_download_url: $versioned}
      ]
    }' > dist/release.json

  delete_version() {
    local version="$1"
    local page=1
    while [ "$page" -le 10 ]; do
      local json count ids id
      json=$(curl --silent --show-error --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
        "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages?package_type=generic&package_name=${PACKAGE_NAME}&per_page=100&page=${page}")
      count=$(printf '%s' "$json" | jq 'if type=="array" then length else 0 end')
      [ "$count" -eq 0 ] && break
      ids=$(printf '%s' "$json" | jq -r --arg v "$version" '.[] | select(.version==$v) | .id')
      for id in $ids; do
        echo "Deleting package ${PACKAGE_NAME}/${version} id=${id}"
        curl --fail --silent --show-error --request DELETE \
          --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
          "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/${id}"
        echo
      done
      [ "$count" -lt 100 ] && break
      page=$((page + 1))
    done
  }

  upload() {
    local version="$1"
    local file="$2"
    local name
    name="$(basename "$file")"
    echo "Uploading ${name} → ${PACKAGE_NAME}/${version}"
    curl --fail --silent --show-error \
      --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
      --upload-file "$file" \
      "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/generic/${PACKAGE_NAME}/${version}/${name}"
    echo
  }

  delete_version "$TAG"
  delete_version "latest"
  local f
  for f in "dist/${APK_NAME}" dist/Log4OM-Android-latest.apk dist/release.json; do
    upload "$TAG" "$f"
    upload "latest" "$f"
  done

  echo "Requesting anonymous package pull on this private project."
  local payload code
  for payload in "package_registry_access_level=public" "package_registry_allow_anyone_to_pull=true"; do
    code=$(curl --silent --output /tmp/pkg-access.txt --write-out "%{http_code}" \
      --request PUT \
      --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
      --data "$payload" \
      "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}" || true)
    echo "PUT ${payload} → HTTP ${code}"
  done

  local manifest_url http apk_url
  manifest_url="${LATEST_BASE}/release.json"
  http=$(curl --silent --output /tmp/manifest-anon.json --write-out "%{http_code}" "$manifest_url" || true)
  echo "Anonymous manifest ${manifest_url} → HTTP ${http}"
  if [ "$http" != "200" ]; then
    echo "The phone cannot download updates (HTTP ${http})."
    echo "A maintainer must allow public package pulls on this private project:"
    echo "  Settings → General → Visibility, project features, permissions"
    echo "  → Package registry → Allow anyone to pull from Package Registry."
    echo "Do not embed a project token in the Android app."
    exit 1
  fi
  apk_url=$(jq -r '.assets[] | select(.name=="Log4OM-Android-latest.apk") | .browser_download_url' /tmp/manifest-anon.json)
  test -n "$apk_url" && [ "$apk_url" != "null" ]
  curl --fail --silent --show-error --output /tmp/probe.apk "$apk_url"
  test -s /tmp/probe.apk
  echo "Anonymous APK download ok (${apk_url})."

  jq -n \
    --arg name "Log4OM Android ${NAME}" \
    --arg tag "$TAG" \
    --arg desc "Automated release from main (pipeline ${CI_PIPELINE_ID}). Version ${NAME} (${CODE}), commit ${CI_COMMIT_SHA}." \
    --arg latest "${VERSIONED_BASE}/Log4OM-Android-latest.apk" \
    --arg versioned "${VERSIONED_BASE}/${APK_NAME}" \
    --arg apk "$APK_NAME" \
    '{
      name: $name,
      tag_name: $tag,
      description: $desc,
      assets: { links: [
        {name: "Log4OM-Android-latest.apk", url: $latest, link_type: "package"},
        {name: $apk, url: $versioned, link_type: "package"}
      ]}
    }' > dist/release-payload.json
  local release_code
  release_code=$(curl --silent --output /tmp/release-resp.json --write-out "%{http_code}" \
    --request POST \
    --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
    --header "Content-Type: application/json" \
    --data @dist/release-payload.json \
    "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases" || true)
  echo "Create GitLab release HTTP ${release_code}"
  if [ "$release_code" != "201" ] && [ "$release_code" != "409" ]; then
    echo "Release object was not created. The package the app downloads is already published."
    cat /tmp/release-resp.json || true
  fi
}
