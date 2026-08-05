#!/usr/bin/env bash
set -euo pipefail

: "${MSC_CANONICAL_KEYSTORE:?Set MSC_CANONICAL_KEYSTORE to the recovered private-test JKS path}"
: "${MSC_CANONICAL_STORE_PASSWORD:?Set MSC_CANONICAL_STORE_PASSWORD}"
: "${MSC_CANONICAL_KEY_ALIAS:?Set MSC_CANONICAL_KEY_ALIAS}"
: "${MSC_CANONICAL_KEY_PASSWORD:?Set MSC_CANONICAL_KEY_PASSWORD}"

INPUT_DIR="${1:?Usage: finalize-0.15.18-canonical.sh INPUT_DIR OUTPUT_DIR}"
OUTPUT_DIR="${2:?Usage: finalize-0.15.18-canonical.sh INPUT_DIR OUTPUT_DIR}"
EXPECTED_SHA1='1997d421d177215a44f9651ce53dbaec152fbc49'
EXPECTED_SHA256='13a3bb08a32ab57c36ea6f885a74b5bd40d70501f93ca9002df7b6cdd0491b2c'

PHONE_INPUT="$INPUT_DIR/phone/MyStudyCompanion-phone-0.15.18-SIGNING-INPUT-NOT-INSTALLABLE.apk"
WEAR_INPUT="$INPUT_DIR/wear/MyStudyCompanion-wear-0.15.18-SIGNING-INPUT-NOT-INSTALLABLE.apk"
test -s "$PHONE_INPUT"
test -s "$WEAR_INPUT"
test -s "$MSC_CANONICAL_KEYSTORE"

if [[ -n "${MSC_ANDROID_BUILD_TOOLS:-}" ]]; then
  BUILD_TOOLS="$MSC_ANDROID_BUILD_TOOLS"
else
  SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
fi
APKSIGNER="$BUILD_TOOLS/apksigner"
if [[ ! -x "$APKSIGNER" && -s "$BUILD_TOOLS/lib/apksigner.jar" ]]; then
  APKSIGNER=(java -jar "$BUILD_TOOLS/lib/apksigner.jar")
else
  APKSIGNER=("$APKSIGNER")
fi
ZIPALIGN="$BUILD_TOOLS/zipalign"
AAPT="$BUILD_TOOLS/aapt"
test -x "$ZIPALIGN"
test -x "$AAPT"

KEY_REPORT="$(mktemp)"
trap 'rm -f "$KEY_REPORT"' EXIT
keytool -list -v \
  -keystore "$MSC_CANONICAL_KEYSTORE" \
  -storepass "$MSC_CANONICAL_STORE_PASSWORD" \
  -alias "$MSC_CANONICAL_KEY_ALIAS" > "$KEY_REPORT"
KEY_SHA1="$(sed -n 's/.*SHA1: //p' "$KEY_REPORT" | tr -d ':' | tr 'A-F' 'a-f' | head -n1)"
KEY_SHA256="$(sed -n 's/.*SHA256: //p' "$KEY_REPORT" | tr -d ':' | tr 'A-F' 'a-f' | head -n1)"
[[ "$KEY_SHA1" == "$EXPECTED_SHA1" ]] || { echo 'Refusing non-Firebase SHA-1 signer.' >&2; exit 1; }
[[ "$KEY_SHA256" == "$EXPECTED_SHA256" ]] || { echo 'Refusing non-canonical SHA-256 signer.' >&2; exit 1; }

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
PHONE_ALIGNED="$OUTPUT_DIR/.phone-aligned.apk"
WEAR_ALIGNED="$OUTPUT_DIR/.wear-aligned.apk"
PHONE_OUTPUT="$OUTPUT_DIR/MyStudyCompanion-0.15.18-Premium-Widgets-FIXED.apk"
WEAR_OUTPUT="$OUTPUT_DIR/MyStudyCompanion-Wear-0.15.18-Premium-Widgets-FIXED.apk"

"$ZIPALIGN" -P 16 -f 4 "$PHONE_INPUT" "$PHONE_ALIGNED"
"$ZIPALIGN" -P 16 -f 4 "$WEAR_INPUT" "$WEAR_ALIGNED"

for pair in "$PHONE_ALIGNED:$PHONE_OUTPUT" "$WEAR_ALIGNED:$WEAR_OUTPUT"; do
  source_apk="${pair%%:*}"
  output_apk="${pair#*:}"
  "${APKSIGNER[@]}" sign \
    --ks "$MSC_CANONICAL_KEYSTORE" \
    --ks-key-alias "$MSC_CANONICAL_KEY_ALIAS" \
    --ks-pass "pass:$MSC_CANONICAL_STORE_PASSWORD" \
    --key-pass "pass:$MSC_CANONICAL_KEY_PASSWORD" \
    --v1-signing-enabled false \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --v4-signing-enabled false \
    --out "$output_apk" "$source_apk"
done
rm -f "$PHONE_ALIGNED" "$WEAR_ALIGNED"

"${APKSIGNER[@]}" verify --verbose --print-certs "$PHONE_OUTPUT" > "$OUTPUT_DIR/PHONE-SIGNATURE.txt"
"${APKSIGNER[@]}" verify --verbose --print-certs "$WEAR_OUTPUT" > "$OUTPUT_DIR/WEAR-SIGNATURE.txt"
"$ZIPALIGN" -c -P 16 -v 4 "$PHONE_OUTPUT" > "$OUTPUT_DIR/PHONE-ALIGNMENT.txt"
"$ZIPALIGN" -c -P 16 -v 4 "$WEAR_OUTPUT" > "$OUTPUT_DIR/WEAR-ALIGNMENT.txt"
"$AAPT" dump badging "$PHONE_OUTPUT" > "$OUTPUT_DIR/PHONE-IDENTITY.txt"
"$AAPT" dump badging "$WEAR_OUTPUT" > "$OUTPUT_DIR/WEAR-IDENTITY.txt"

grep -Fq "Signer #1 certificate SHA-256 digest: $EXPECTED_SHA256" "$OUTPUT_DIR/PHONE-SIGNATURE.txt"
grep -Fq "Signer #1 certificate SHA-1 digest: $EXPECTED_SHA1" "$OUTPUT_DIR/PHONE-SIGNATURE.txt"
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/PHONE-SIGNATURE.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$OUTPUT_DIR/PHONE-SIGNATURE.txt"
grep -Fq "Signer #1 certificate SHA-256 digest: $EXPECTED_SHA256" "$OUTPUT_DIR/WEAR-SIGNATURE.txt"
grep -Fq "package: name='com.mystudycompanion.app' versionCode='51' versionName='0.15.18-private-alpha-premium-widgets'" "$OUTPUT_DIR/PHONE-IDENTITY.txt"
grep -Fq "package: name='com.mystudycompanion.app' versionCode='360168001' versionName='0.15.18-wear-private-alpha-premium-widgets'" "$OUTPUT_DIR/WEAR-IDENTITY.txt"

python3 - "$PHONE_INPUT" "$PHONE_OUTPUT" "$WEAR_INPUT" "$WEAR_OUTPUT" "$OUTPUT_DIR/PAYLOAD-PARITY.json" <<'PY'
from pathlib import Path
import hashlib
import json
import sys
import zipfile

pairs = [(Path(sys.argv[1]), Path(sys.argv[2]), 'phone'), (Path(sys.argv[3]), Path(sys.argv[4]), 'wear')]
report = {}
for before, after, label in pairs:
    def payload(path: Path) -> dict[str, str]:
        result = {}
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                if entry.filename.startswith('META-INF/'):
                    continue
                result[entry.filename] = hashlib.sha256(archive.read(entry.filename)).hexdigest()
        return result
    old = payload(before)
    new = payload(after)
    missing = sorted(set(old) - set(new))
    extra = sorted(set(new) - set(old))
    changed = sorted(name for name in old.keys() & new.keys() if old[name] != new[name])
    report[label] = {
        'input_entries': len(old),
        'final_entries': len(new),
        'missing': missing,
        'extra': extra,
        'changed': changed,
    }
    if missing or extra or changed:
        raise SystemExit(f'{label} payload changed during signing')
Path(sys.argv[5]).write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
PY

(
  cd "$OUTPUT_DIR"
  sha256sum \
    MyStudyCompanion-0.15.18-Premium-Widgets-FIXED.apk \
    MyStudyCompanion-Wear-0.15.18-Premium-Widgets-FIXED.apk \
    > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)

echo 'PASS: 0.15.18 was finalized with the exact Firebase-registered canonical signer.'
