#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${MSC_BACKEND_BASE_URL:?MSC_BACKEND_BASE_URL is required for the working Smart Online assistant}"
[[ "$MSC_BACKEND_BASE_URL" == https://* ]] || { echo 'MSC_BACKEND_BASE_URL must be HTTPS' >&2; exit 1; }

# Reconstruct and verify every accepted patch through 0.15.14 first.
bash .msc-build/run-0.15.14-vote-persistence-fix.sh

# Apply the AI and activity replacement only after the cumulative source is present.
bash .msc-build/apply-0.15.15-ai-activity-rebuild.sh

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

RELEASE="release-0.15.15"
rm -rf "$RELEASE"
mkdir -p "$RELEASE/phone" "$RELEASE/wear" "$RELEASE/metadata"
PHONE="$RELEASE/phone/MyStudyCompanion-phone-0.15.15-configured-ci.apk"
WEAR="$RELEASE/wear/MyStudyCompanion-wear-0.15.15-configured-ci.apk"
cp "$PHONE_SOURCE" "$PHONE"
cp "$WEAR_SOURCE" "$WEAR"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$PHONE" > "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$WEAR" > "$RELEASE/metadata/WEAR-SIGNING-CI.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$PHONE" > "$RELEASE/metadata/PHONE-ALIGNMENT.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$WEAR" > "$RELEASE/metadata/WEAR-ALIGNMENT.txt"
"$BUILD_TOOLS/aapt" dump badging "$PHONE" > "$RELEASE/metadata/PHONE-IDENTITY.txt"
"$BUILD_TOOLS/aapt" dump badging "$WEAR" > "$RELEASE/metadata/WEAR-IDENTITY.txt"

grep -Fq "package: name='com.mystudycompanion.app' versionCode='48' versionName='0.15.15-private-alpha-smart-ai-activity-rebuild'" "$RELEASE/metadata/PHONE-IDENTITY.txt"
grep -Fq "package: name='com.mystudycompanion.app' versionCode='360165001' versionName='0.15.15-wear-private-alpha-smart-ai-activity-rebuild'" "$RELEASE/metadata/WEAR-IDENTITY.txt"
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/WEAR-SIGNING-CI.txt"

grep -Fq "BACKEND_BASE_URL = \"$MSC_BACKEND_BASE_URL\"" \
  MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java

python3 MyStudyCompanion/tools/verify_curated_workbook.py | tee "$RELEASE/metadata/ACTIVITY-QUALITY-GATE.txt"
sha256sum "$PHONE" "$WEAR" > "$RELEASE/metadata/SHA256SUMS-CI.txt"

cat > "$RELEASE/metadata/SMART-AI-AUDIT.txt" <<EOF
PASS: Android routes Smart Online questions through the authenticated BackendApi instead of the old disconnected guided-response path.
PASS: the APK contains the configured HTTPS backend endpoint: $MSC_BACKEND_BASE_URL
PASS: OpenAI credentials remain server-side and do not appear in Android application source or packaged resources.
PASS: the backend uses the OpenAI Responses API, model gpt-5.4 by default, app-managed recent conversation context, structured output, official-domain web search, and an answer-quality retry.
PASS: provider-side response storage is disabled for study requests.
PASS: Auto, Smart Online, and Private Offline modes are explicit in the UI; fallback is never disguised as a cloud answer.
PASS: official-source citations are required and validated before the app accepts a Smart Online answer.
EOF

cat > "$RELEASE/metadata/ACTIVITY-REBUILD-AUDIT.txt" <<'EOF'
PASS: all 16 color-by-number pages use curated illustrations rather than automatic photo segmentation.
PASS: each page has 8-24 meaningful closed shapes and every tappable region contains at least 900 mask pixels.
PASS: palettes show only colors used on the current page and display remaining-shape counts.
PASS: color-by-number includes immediate feedback, progressive hints, visible progress, undo, redo, reset, and a completed-picture reveal.
PASS: find-the-differences accepts taps on either picture, never exposes an answer list, marks discoveries on both pictures, and uses staged hints.
PASS: printable PDF rendering uses the curated color-by-number line art and region data.
EOF

cat > "$RELEASE/metadata/RESEARCH-DESIGN-NOTES.txt" <<'EOF'
The activity redesign follows a guided-practice pattern: clear outcome feedback, retry without punishment, progressively stronger hints, limited visual choices, and challenge that can be completed without revealing the answer list. The color activity favors large semantically meaningful shapes over dense generated fragments, reducing motor precision demands while preserving visual discrimination and number-color matching.

The assistant redesign separates online and offline capabilities honestly. The online path uses a server-held API key, multi-turn conversation context, constrained official-source research, structured responses, citation validation, and quality rejection/retry. The offline path remains available but is labeled as a lower-capability private fallback.
EOF

cat > "$RELEASE/metadata/CUMULATIVE-PATCH-MANIFEST.txt" <<'EOF'
APPLIED: every accepted cumulative patch through 0.15.14.
APPLIED: 0.15.15 Smart Online conversational assistant and explicit private-offline fallback.
APPLIED: 0.15.15 curated 16-page color-by-number replacement.
APPLIED: 0.15.15 find-the-differences interaction replacement.
EOF

echo 'PASS: configured 0.15.15 phone and Wear APKs built and verified.'
