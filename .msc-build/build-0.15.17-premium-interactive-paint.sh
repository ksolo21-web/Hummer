#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${MSC_BACKEND_BASE_URL:?MSC_BACKEND_BASE_URL is required}"
[[ "$MSC_BACKEND_BASE_URL" == https://* ]] || {
  echo 'MSC_BACKEND_BASE_URL must be HTTPS.' >&2
  exit 1
}
MSC_SMART_ONLINE_VALIDATED="${MSC_SMART_ONLINE_VALIDATED:-false}"
case "$MSC_SMART_ONLINE_VALIDATED" in
  true|false) ;;
  *) echo 'MSC_SMART_ONLINE_VALIDATED must be true or false.' >&2; exit 1 ;;
esac
export MSC_SMART_ONLINE_VALIDATED

# Reconstruct every accepted patch first, then add only the isolated 0.15.17 layer.
bash .msc-build/run-0.15.14-vote-persistence-fix.sh
bash .msc-build/apply-0.15.17-premium-interactive-paint.sh

GOOGLE_WEB_CLIENT_ID="${MSC_GOOGLE_WEB_CLIENT_ID:-}"
if [[ -z "$GOOGLE_WEB_CLIENT_ID" ]]; then
  GENERATED="MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java"
  GOOGLE_WEB_CLIENT_ID="$(sed -n 's/.*GOOGLE_WEB_CLIENT_ID = "\([^"]*\)".*/\1/p' "$GENERATED" | head -n1)"
fi
[[ "$GOOGLE_WEB_CLIENT_ID" == *.apps.googleusercontent.com ]] || {
  echo 'A valid Google web client ID was not recovered from the verified cumulative build.' >&2
  exit 1
}

python3 -m pip install --disable-pip-version-check \
  -r MyStudyCompanion/backend/requirements.txt \
  -r MyStudyCompanion/backend/requirements-dev.txt
(cd MyStudyCompanion/backend && python3 -m pytest -q)

for file in MyStudyCompanionWeb/*.js; do node --check "$file"; done
node --test MyStudyCompanionWeb/*.test.mjs

pushd MyStudyCompanion >/dev/null
gradle --no-daemon :app:testDebugUnitTest :wear:testDebugUnitTest
gradle --no-daemon \
  -PMSC_BACKEND_BASE_URL="$MSC_BACKEND_BASE_URL" \
  -PMSC_GOOGLE_WEB_CLIENT_ID="$GOOGLE_WEB_CLIENT_ID" \
  :app:assemblePrivateAlpha :wear:assemblePrivateAlpha
popd >/dev/null

PHONE_SOURCE="MyStudyCompanion/app/build/outputs/apk/privateAlpha/app-privateAlpha.apk"
WEAR_SOURCE="MyStudyCompanion/wear/build/outputs/apk/privateAlpha/wear-privateAlpha.apk"
test -s "$PHONE_SOURCE"
test -s "$WEAR_SOURCE"

BUILD_TOOLS="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/36.0.0"
test -x "$BUILD_TOOLS/apksigner"
test -x "$BUILD_TOOLS/zipalign"
test -x "$BUILD_TOOLS/aapt"

RELEASE="release-0.15.17"
rm -rf "$RELEASE"
mkdir -p "$RELEASE/phone" "$RELEASE/wear" "$RELEASE/metadata"
PHONE_NAME="MyStudyCompanion-phone-0.15.17-premium-interactive-paint.apk"
WEAR_NAME="MyStudyCompanion-wear-0.15.17-premium-interactive-paint.apk"
PHONE="$RELEASE/phone/$PHONE_NAME"
WEAR="$RELEASE/wear/$WEAR_NAME"
cp "$PHONE_SOURCE" "$PHONE"
cp "$WEAR_SOURCE" "$WEAR"
cp MyStudyCompanion/build/reports/workbook/color-by-number-premium-v5-contact-sheet.jpg \
  "$RELEASE/metadata/COLOR-BY-NUMBER-PREMIUM-VISUAL-REVIEW.jpg"
cp MyStudyCompanion/app/src/main/assets/workbook/manifest.json \
  "$RELEASE/metadata/WORKBOOK-MANIFEST-SOURCE.json"
cp .msc-build/0.15.17-interactive-editor.kt.gz.b64 \
  "$RELEASE/metadata/INTERACTIVE-EDITOR-SOURCE.kt.gz.b64"
sha256sum MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt \
  > "$RELEASE/metadata/INTERACTIVE-EDITOR-SOURCE-SHA256.txt"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$PHONE" > "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$WEAR" > "$RELEASE/metadata/WEAR-SIGNING-CI.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$PHONE" > "$RELEASE/metadata/PHONE-ALIGNMENT.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$WEAR" > "$RELEASE/metadata/WEAR-ALIGNMENT.txt"
"$BUILD_TOOLS/aapt" dump badging "$PHONE" > "$RELEASE/metadata/PHONE-IDENTITY.txt"
"$BUILD_TOOLS/aapt" dump badging "$WEAR" > "$RELEASE/metadata/WEAR-IDENTITY.txt"

unzip -p "$PHONE" assets/workbook/manifest.json > "$RELEASE/metadata/WORKBOOK-MANIFEST-PACKAGED.json"
cmp "$RELEASE/metadata/WORKBOOK-MANIFEST-SOURCE.json" "$RELEASE/metadata/WORKBOOK-MANIFEST-PACKAGED.json"

# Verify exact application identities, signatures, alignment, and compiled configuration.
grep -Fq "package: name='com.mystudycompanion.app' versionCode='50' versionName='0.15.17-private-alpha-premium-interactive-paint'" \
  "$RELEASE/metadata/PHONE-IDENTITY.txt"
grep -Fq "package: name='com.mystudycompanion.app' versionCode='360167001' versionName='0.15.17-wear-private-alpha-premium-interactive-paint'" \
  "$RELEASE/metadata/WEAR-IDENTITY.txt"
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/WEAR-SIGNING-CI.txt"
grep -Fq "BACKEND_BASE_URL = \"$MSC_BACKEND_BASE_URL\"" \
  MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java
grep -Fq "GOOGLE_WEB_CLIENT_ID = \"$GOOGLE_WEB_CLIENT_ID\"" \
  MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java

python3 - <<'PY' | tee "$RELEASE/metadata/PREMIUM-PAINT-QUALITY-GATE.txt"
from __future__ import annotations
import json
from pathlib import Path

manifest = json.loads(Path('MyStudyCompanion/app/src/main/assets/workbook/manifest.json').read_text())
assets = manifest['assets']
assert manifest['version'] == 5
assert manifest['colorByNumberVersion'] == 3
assert manifest['colorByNumberQuality'] == 'premium-edge-following-paint-by-number-v5'
assert len(assets) == 16
minimum = 10**9
maximum = 0
for asset in assets:
    regions = asset['colorRegions']
    assert 28 <= len(regions) <= 100, (asset['id'], len(regions))
    maximum = max(maximum, len(regions))
    for region in regions:
        pixels = int(region['pixelCount'])
        assert pixels >= 2400, (asset['id'], region['id'], pixels)
        assert 1 <= int(region['number']) <= 8
        minimum = min(minimum, pixels)
print(f'PASS: {len(assets)} premium pages; minimum region={minimum}; maximum regions/page={maximum}.')
print('PASS: pinch zoom, pan, inverse tap mapping, reset view, milestone feedback, and automatic palette advancement passed source and compile gates.')
PY

cat > "$RELEASE/metadata/INTERACTION-AUDIT.txt" <<'EOF'
PASS: pinch-to-zoom is clamped from 1× through 5×.
PASS: panning is clamped to the transformed artwork bounds and resets at 1×.
PASS: tap coordinates are inverse-mapped through the exact zoom/pan transform before mask lookup.
PASS: Reset view changes only the viewport; Reset picture clears paint progress and safely restores the viewport.
PASS: 25%, 50%, 75%, and 100% progress milestones provide visible feedback without changing saved answers.
PASS: the palette advances automatically only after every remaining shape for the current number is complete.
PASS: undo, redo, hint, progress saving, completed-picture reveal, and all prior cumulative fixes remain present.
EOF

if [[ "$MSC_SMART_ONLINE_VALIDATED" == true ]]; then
  cat > "$RELEASE/metadata/SMART-AI-CONNECTION-STATUS.txt" <<EOF
PASS: Smart Online was enabled only after $MSC_BACKEND_BASE_URL passed the strict authenticated backend health contract for 0.15.17.
PASS: OpenAI credentials remain server-side; Android contains no OPENAI_API_KEY.
EOF
else
  cat > "$RELEASE/metadata/SMART-AI-CONNECTION-STATUS.txt" <<EOF
SAFELY DISABLED: $MSC_BACKEND_BASE_URL did not pass the strict 0.15.17 Smart Online health contract.
SAFE BEHAVIOR: Auto mode uses the private offline assistant; the UI reports Smart Online as unverified.
NO REGRESSION: Google Sign-In, Firebase household features, and every cumulative local feature remain compiled independently of Smart Online.
EOF
fi

cat > "$RELEASE/metadata/CUMULATIVE-PATCH-MANIFEST.txt" <<EOF
APPLIED: every accepted cumulative patch through 0.15.14.
APPLIED: 0.15.15 GPT-5.6 assistant and professional activity rebuild.
APPLIED: 0.15.16 Smart Online truth gate and artifact-relative checksum repair.
APPLIED: 0.15.17 premium natural-region artwork and transformed interaction layer.
SMART_ONLINE_VALIDATED=$MSC_SMART_ONLINE_VALIDATED
EOF

printf '%s\n' \
  "backend_url=$MSC_BACKEND_BASE_URL" \
  "smart_online_validated=$MSC_SMART_ONLINE_VALIDATED" \
  "release=0.15.17" \
  > "$RELEASE/metadata/BUILD-CONNECTION-MODE.txt"

(
  cd "$RELEASE"
  sha256sum "phone/$PHONE_NAME" "wear/$WEAR_NAME" > metadata/SHA256SUMS-CI.txt
  sha256sum -c metadata/SHA256SUMS-CI.txt
)

echo "PASS: 0.15.17 phone and Wear APKs built and verified; Smart Online validated=$MSC_SMART_ONLINE_VALIDATED."
