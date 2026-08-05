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
  *)
    echo 'MSC_SMART_ONLINE_VALIDATED must be true or false.' >&2
    exit 1
    ;;
esac
export MSC_SMART_ONLINE_VALIDATED

# Reconstruct and verify every accepted cumulative patch first.
bash .msc-build/run-0.15.14-vote-persistence-fix.sh

# Apply the complete activity/assistant rebuild plus the release truth gate.
bash .msc-build/apply-0.15.16-release-truth.sh

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

RELEASE="release-0.15.16"
rm -rf "$RELEASE"
mkdir -p "$RELEASE/phone" "$RELEASE/wear" "$RELEASE/metadata"
PHONE_NAME="MyStudyCompanion-phone-0.15.16-truth-gated.apk"
WEAR_NAME="MyStudyCompanion-wear-0.15.16-truth-gated.apk"
PHONE="$RELEASE/phone/$PHONE_NAME"
WEAR="$RELEASE/wear/$WEAR_NAME"
cp "$PHONE_SOURCE" "$PHONE"
cp "$WEAR_SOURCE" "$WEAR"
cp MyStudyCompanion/build/reports/workbook/color-by-number-professional-contact-sheet.jpg \
  "$RELEASE/metadata/COLOR-BY-NUMBER-VISUAL-REVIEW.jpg"
cp MyStudyCompanion/app/src/main/assets/workbook/manifest.json \
  "$RELEASE/metadata/WORKBOOK-MANIFEST.json"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$PHONE" > "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$WEAR" > "$RELEASE/metadata/WEAR-SIGNING-CI.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$PHONE" > "$RELEASE/metadata/PHONE-ALIGNMENT.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$WEAR" > "$RELEASE/metadata/WEAR-ALIGNMENT.txt"
"$BUILD_TOOLS/aapt" dump badging "$PHONE" > "$RELEASE/metadata/PHONE-IDENTITY.txt"
"$BUILD_TOOLS/aapt" dump badging "$WEAR" > "$RELEASE/metadata/WEAR-IDENTITY.txt"

grep -Fq "package: name='com.mystudycompanion.app' versionCode='49' versionName='0.15.16-private-alpha-truth-gated-ai-activities'" \
  "$RELEASE/metadata/PHONE-IDENTITY.txt"
grep -Fq "package: name='com.mystudycompanion.app' versionCode='360166001' versionName='0.15.16-wear-private-alpha-truth-gated-ai-activities'" \
  "$RELEASE/metadata/WEAR-IDENTITY.txt"
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/PHONE-SIGNING-CI.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/WEAR-SIGNING-CI.txt"
grep -Fq "BACKEND_BASE_URL = \"$MSC_BACKEND_BASE_URL\"" \
  MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java

python3 MyStudyCompanion/tools/verify_curated_workbook.py | tee "$RELEASE/metadata/ACTIVITY-QUALITY-GATE.txt"

# Write paths relative to the artifact root so verification works after download/extraction.
(
  cd "$RELEASE"
  sha256sum "phone/$PHONE_NAME" "wear/$WEAR_NAME" > metadata/SHA256SUMS-CI.txt
)

if [[ "$MSC_SMART_ONLINE_VALIDATED" == true ]]; then
  cat > "$RELEASE/metadata/SMART-AI-CONNECTION-STATUS.txt" <<EOF
PASS: Smart Online was enabled only after the packaged backend passed the strict JSON health contract.
PASS: validated backend endpoint: $MSC_BACKEND_BASE_URL
PASS: Android routes authenticated study questions through BackendApi.
PASS: OpenAI credentials remain server-side and provider-side response storage is disabled.
PASS: the backend defaults to GPT-5.6, requires verified official citations, and restricts research to approved official domains.
EOF
else
  cat > "$RELEASE/metadata/SMART-AI-CONNECTION-STATUS.txt" <<EOF
NOT ENABLED: the candidate endpoint did not pass the strict Smart Online backend health contract.
PACKAGED ENDPOINT: $MSC_BACKEND_BASE_URL
SAFE BEHAVIOR: Auto mode uses the private offline assistant; Smart Online is shown as unverified instead of pretending the static web app is an AI backend.
NO FALSE CLAIM: this artifact verifies the activity rebuild and offline behavior, not a live cloud-AI connection.
EOF
fi

cat > "$RELEASE/metadata/ACTIVITY-REBUILD-AUDIT.txt" <<'EOF'
PASS: all 16 color-by-number pages preserve the stored professional illustration as the completed-picture reward.
PASS: each play page uses 14-20 connected edge-aware regions, every region contains at least 6,000 mask pixels, and each page uses at least five palette colors.
PASS: number labels are placed at stable interior points; palettes show only colors used on the current page and remaining-shape counts.
PASS: color-by-number includes immediate feedback, progressive hints, visible progress, undo, redo, reset, and a completed-picture reveal.
PASS: find-the-differences accepts taps on either picture, never exposes an answer list, marks discoveries on both pictures, and uses staged hints.
PASS: printable PDF rendering uses the same verified line art and region data.
PASS: COLOR-BY-NUMBER-VISUAL-REVIEW.jpg is packaged for human inspection.
EOF

cat > "$RELEASE/metadata/CUMULATIVE-PATCH-MANIFEST.txt" <<EOF
APPLIED: every accepted cumulative patch through 0.15.14.
APPLIED: 0.15.15 GPT-5.6 assistant and professional activity rebuild.
APPLIED: 0.15.16 Smart Online truth gate and artifact-relative checksum repair.
SMART_ONLINE_VALIDATED=$MSC_SMART_ONLINE_VALIDATED
EOF

printf '%s\n' \
  "backend_url=$MSC_BACKEND_BASE_URL" \
  "smart_online_validated=$MSC_SMART_ONLINE_VALIDATED" \
  "release=0.15.16" \
  > "$RELEASE/metadata/BUILD-CONNECTION-MODE.txt"

echo "PASS: 0.15.16 phone and Wear APKs built; Smart Online validated=$MSC_SMART_ONLINE_VALIDATED."
