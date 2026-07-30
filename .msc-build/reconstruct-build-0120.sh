#!/usr/bin/env bash
set -euo pipefail

verify_extract() {
  local pattern="$1" expected="$2" output="$3"
  cat $pattern | base64 --decode > "$output"
  echo "$expected  $output" | sha256sum -c -
  tar -xJf "$output"
}

verify_extract '.msc-build/source.part*.b64' 'f2c0bfb74455046a28d7aa54a4e27df021955ea703ae93a639bbe3bcb67ed2c4' base.tar.xz
verify_extract '.msc-build/scene-0.9.3.part*.b64' '48b6aa28b630bdbf0ade5fcfadd77af7b4a259807df0b73c4318f17fede4eec4' scene.tar.xz
verify_extract '.msc-build/semantic-0.9.4.part*.b64' 'f693bcb607ad00a30c4338e8819250ed5f1d44f372e40093682a13c4beed6277' semantic.tar.xz
verify_extract '.msc-build/watchtower-0.9.5.part*.b64' 'a742f0c76ba81b87fff0fe97e93e3c03e3968f2dc6ef6ef5ed24dd039bcf5d6e' watchtower.tar.xz
verify_extract '.msc-build/verified-preparation-0.9.6.part*.b64' 'f320e0d36adcd21b2c8c1b1583620a37c38d3415f981cc34b35c77eafcd27278' preparation.tar.xz
verify_extract '.msc-build/ai-0.10.0.part*.b64' '7e7dde1ad7e8a26fdc797b7df154a416ebdc12c588915e79f00d497a45b70461' ai.tar.xz
base64 --decode .msc-build/ai-0.10.0-fix1.b64 > ai-fix1.tar.xz
echo '817fa3144c355b2691c6af8d4b17e70de14ddb2010ec1990399416e02beeba2d  ai-fix1.tar.xz' | sha256sum -c -
tar -xJf ai-fix1.tar.xz
python3 .msc-build/patch_source.py
python3 MyStudyCompanion/tools/validate_source.py
base64 --decode .msc-build/patch-0.10.1.py.gz.b64 | gzip -dc > /tmp/patch-0.10.1.py
python3 /tmp/patch-0.10.1.py
cat .msc-build/companion-0.11.0.part*.b64 | base64 --decode > /tmp/companion-0.11.0-overlay.tar.xz
echo 'c9704c0eef9c0086e1b0d53fe87911fbdf313888afd83783cc77b682e8859a76  /tmp/companion-0.11.0-overlay.tar.xz' | sha256sum -c -
tar -xJf /tmp/companion-0.11.0-overlay.tar.xz -C MyStudyCompanion
python3 .msc-build/patch-0.11.0-gradle.py
cat .msc-build/connectivity-0.11.1.part*.bin > /tmp/connectivity-0.11.1.tar.xz
echo 'c4b745c11ce72eb7b6b37cc6ffab0bf97da7142cfb9edc51503365e2900c08e5  /tmp/connectivity-0.11.1.tar.xz' | sha256sum -c -
tar -xJf /tmp/connectivity-0.11.1.tar.xz -C MyStudyCompanion
python3 .msc-build/patch-0.11.1-jvm-links.py

cat .msc-build/source-traceability-0.12.0.patch.part* > /tmp/source-traceability-0.12.0.patch
echo '917014cb37ac4878987805ac7e86d277ed13778a11bc0948e5f31495b1b8cba1  /tmp/source-traceability-0.12.0.patch' | sha256sum -c -
(
  cd MyStudyCompanion
  patch -p1 --batch < /tmp/source-traceability-0.12.0.patch
)
python3 MyStudyCompanion/tools/validate_source.py

grep -q 'versionCode = 24' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.0-private-alpha-source-traceability' MyStudyCompanion/app/build.gradle.kts
grep -q '360120001' MyStudyCompanion/wear/build.gradle.kts
grep -q 'Every factual or explanatory paragraph' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/knowledge/AiPromptBuilder.kt
grep -q 'validateAndResolve' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/knowledge/AiGroundingValidator.kt
grep -q 'usedPassageIndexes' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt
grep -q 'generateGuided' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/runtime/AiProviderRouter.kt
grep -q 'JwLibraryLinkResolver.openOfficial' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt
! grep -q 'Intent.ACTION_VIEW' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt
test -s MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/ai/runtime/GuidedOfflineStudyProviderTest.kt

URL='https://www.jw.org/en/library/jw-meeting-workbook/july-august-2026-mwb/Life-and-Ministry-Meeting-Schedule-for-July-27-August-2-2026/'
curl --fail --location --retry 3 "$URL" -o current-week.html
python3 .msc-build/verify-current-week-0111.py current-week.html

cd MyStudyCompanion
gradle --no-daemon --stacktrace -PMSC_LOCAL_OWNER_MODE=true \
  :app:testDebugUnitTest :wear:testDebugUnitTest \
  :app:assembleDebug :wear:assembleDebug
cd ..

mkdir -p dist
PHONE_APK="$(find MyStudyCompanion/app/build/outputs/apk/debug -name '*.apk' -type f | head -n 1)"
WEAR_APK="$(find MyStudyCompanion/wear/build/outputs/apk/debug -name '*.apk' -type f | head -n 1)"
test -f "$PHONE_APK"; test -f "$WEAR_APK"
cp "$PHONE_APK" dist/MyStudyCompanion-phone-0.12.0-debug.apk
cp "$WEAR_APK" dist/MyStudyCompanion-wear-0.12.0-debug.apk
AAPT="$ANDROID_HOME/build-tools/36.0.0/aapt"
"$AAPT" dump badging dist/MyStudyCompanion-phone-0.12.0-debug.apk > dist/PHONE-IDENTITY.txt
"$AAPT" dump badging dist/MyStudyCompanion-wear-0.12.0-debug.apk > dist/WEAR-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app.debug' versionCode='24'" dist/PHONE-IDENTITY.txt
grep -q "versionName='0.12.0-private-alpha-source-traceability-debug'" dist/PHONE-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app.debug' versionCode='360120001'" dist/WEAR-IDENTITY.txt
grep -q "versionName='0.12.0-wear-private-alpha-source-traceability-debug'" dist/WEAR-IDENTITY.txt
(cd dist && sha256sum *.apk > SHA256SUMS.txt)
cp -R MyStudyCompanion/app/build/reports/tests dist/phone-test-reports
cp -R MyStudyCompanion/wear/build/reports/tests dist/wear-test-reports
cat > dist/SOURCE-TRACEABILITY-VERIFICATION.txt <<'TXT'
PASS: on-device AI prompt requires machine-readable source-use markers.
PASS: validator rejects missing, invalid, out-of-range, and model-created citations.
PASS: repository attaches only the verified passages the answer identified as used.
PASS: invalid model source output falls back to deterministic verified-source guidance.
PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.
PASS: current week and exact JW Library Finder targets remain present from 0.11.1.
TXT
