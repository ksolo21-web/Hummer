#!/usr/bin/env bash
set -euo pipefail

WORKSPACE="$(pwd)"
BASELINE_WORK="$(mktemp -d)"
BASELINE_OUT="$WORKSPACE/.msc-build/MyStudyCompanion-phone-0.12.0-migration-baseline-debug.apk"
export BASELINE_OUT
cleanup() {
  rm -rf "$BASELINE_WORK"
}
trap cleanup EXIT

# Create one test-only debug signer before either source stage is built. Both APKs
# are compiled during this workflow run and therefore carry the same certificate,
# which is required for Android to perform a genuine in-place Room migration.
mkdir -p "$HOME/.android"
rm -f "$HOME/.android/debug.keystore"
keytool -genkeypair -noprompt \
  -keystore "$HOME/.android/debug.keystore" \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -dname 'CN=My Study Companion Migration Test,O=Private Verification,C=US' \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Reconstruct the authentic 0.12.0 source stage in an isolated directory. The
# existing build script already has a precise boundary immediately before the
# 0.12.1 hardening overlay, so derive the baseline builder from that boundary
# instead of maintaining a second, drifting reconstruction recipe.
cp -a .msc-build "$BASELINE_WORK/.msc-build"
python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0120.sh').read_text(encoding='utf-8')
marker = "base64 --decode .msc-build/patch-0.12.1-hardening.py.xz.b64"
if source.count(marker) != 1:
    raise SystemExit('Expected exactly one 0.12.1 hardening boundary.')
prefix = source.split(marker, 1)[0]
append = r'''
grep -q 'versionCode = 24' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.0-private-alpha-source-traceability' MyStudyCompanion/app/build.gradle.kts
cd MyStudyCompanion
gradle --no-daemon --stacktrace -PMSC_LOCAL_OWNER_MODE=true :app:assembleDebug
cd ..
BASELINE_APK="$(find MyStudyCompanion/app/build/outputs/apk/debug -name '*.apk' -type f | head -n 1)"
test -f "$BASELINE_APK"
cp "$BASELINE_APK" "${BASELINE_OUT:?}"
'''
Path('/tmp/reconstruct-0120-migration-baseline.sh').write_text(
    prefix + append,
    encoding='utf-8',
)
PY
(
  cd "$BASELINE_WORK"
  bash /tmp/reconstruct-0120-migration-baseline.sh
)
test -f "$BASELINE_OUT"

# Build the hardened 0.12.1 phone and Wear packages with the same signer.
bash .msc-build/reconstruct-build-0120.sh

BASELINE_NAME='MyStudyCompanion-phone-0.12.0-migration-baseline-debug.apk'
cp "$BASELINE_OUT" "dist/$BASELINE_NAME"
AAPT="$ANDROID_HOME/build-tools/36.0.0/aapt"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
test -x "$AAPT"
test -x "$APKSIGNER"

"$AAPT" dump badging "dist/$BASELINE_NAME" > dist/MIGRATION-BASELINE-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app.debug' versionCode='24'" dist/MIGRATION-BASELINE-IDENTITY.txt
grep -q "versionName='0.12.0-private-alpha-source-traceability-debug'" dist/MIGRATION-BASELINE-IDENTITY.txt

"$APKSIGNER" verify --verbose --print-certs "dist/$BASELINE_NAME" > dist/MIGRATION-BASELINE-SIGNATURE.txt
"$APKSIGNER" verify --verbose --print-certs dist/MyStudyCompanion-phone-0.12.1-debug.apk > dist/PHONE-SIGNATURE.txt
BASELINE_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' dist/MIGRATION-BASELINE-SIGNATURE.txt | head -n 1)"
CURRENT_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' dist/PHONE-SIGNATURE.txt | head -n 1)"
test -n "$BASELINE_CERT"
test -n "$CURRENT_CERT"
[[ "$BASELINE_CERT" == "$CURRENT_CERT" ]]
printf 'PASS: 0.12.0 migration baseline and 0.12.1 phone APK share signer certificate SHA-256 %s.\n' "$CURRENT_CERT" \
  | tee dist/MIGRATION-SIGNING-CONTINUITY.txt

(
  cd dist
  sha256sum *.apk > SHA256SUMS.txt
)
cat >> dist/GROUNDED-LINKS-VERIFICATION.txt <<'TXT'
PASS: the real 0.12.0 source stage is bundled as a migration baseline.
PASS: the 0.12.0 migration baseline and 0.12.1 phone APK are built in one run with the same test signer, enabling a genuine in-place Room v6-to-v7 upgrade.
TXT
