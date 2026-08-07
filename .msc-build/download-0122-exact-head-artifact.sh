#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN is required}"
: "${EXPECTED_SHA:?EXPECTED_SHA is required}"
ARTIFACT_NAME='msc-0.12.2-complete-jw-links-debug-bundle'
ARTIFACT_ID=''

for attempt in $(seq 1 100); do
  curl --fail --location --retry 3 \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    "https://api.github.com/repos/ksolo21-web/Hummer/actions/artifacts?name=${ARTIFACT_NAME}&per_page=100" \
    -o artifacts.json
  ARTIFACT_ID="$(python3 - "$EXPECTED_SHA" <<'PY'
import json
import sys
expected = sys.argv[1]
data = json.load(open('artifacts.json', encoding='utf-8'))
items = [
    artifact for artifact in data.get('artifacts', [])
    if not artifact.get('expired')
    and artifact.get('workflow_run', {}).get('head_sha') == expected
]
items.sort(key=lambda item: item.get('created_at', ''), reverse=True)
print(items[0]['id'] if items else '')
PY
  )"
  [[ -n "$ARTIFACT_ID" ]] && break
  echo "Waiting for ${ARTIFACT_NAME} from exact head ${EXPECTED_SHA} (attempt ${attempt}/100)."
  sleep 15
done

test -n "$ARTIFACT_ID"
curl --fail --location --retry 3 \
  -H "Authorization: Bearer ${GH_TOKEN}" \
  -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2022-11-28' \
  "https://api.github.com/repos/ksolo21-web/Hummer/actions/artifacts/${ARTIFACT_ID}/zip" \
  -o debug-bundle.zip
rm -rf dist
mkdir -p dist
unzip -q debug-bundle.zip -d dist
(cd dist && sha256sum -c SHA256SUMS.txt)
test -f dist/MyStudyCompanion-phone-0.12.0-migration-baseline-debug.apk
test -f dist/MyStudyCompanion-phone-0.12.2-debug.apk
test -f dist/MyStudyCompanion-wear-0.12.2-debug.apk
printf 'PASS: downloaded artifact %s from exact head %s and verified every APK checksum.\n' \
  "$ARTIFACT_ID" "$EXPECTED_SHA" | tee dist/EXACT-HEAD-ARTIFACT.txt
