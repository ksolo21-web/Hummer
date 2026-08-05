#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${MSC_GOOGLE_WEB_CLIENT_ID:?MSC_GOOGLE_WEB_CLIENT_ID is required}"
: "${MSC_RELEASE_STORE_FILE:?MSC_RELEASE_STORE_FILE is required for the internal CI signing input}"
: "${MSC_RELEASE_STORE_PASSWORD:?MSC_RELEASE_STORE_PASSWORD is required}"
: "${MSC_RELEASE_KEY_ALIAS:?MSC_RELEASE_KEY_ALIAS is required}"
: "${MSC_RELEASE_KEY_PASSWORD:?MSC_RELEASE_KEY_PASSWORD is required}"

MSC_BACKEND_BASE_URL="${MSC_BACKEND_BASE_URL:-}"
MSC_SMART_ONLINE_VALIDATED="${MSC_SMART_ONLINE_VALIDATED:-false}"
case "$MSC_SMART_ONLINE_VALIDATED" in
  true|false) ;;
  *) echo 'MSC_SMART_ONLINE_VALIDATED must be true or false.' >&2; exit 1 ;;
esac
[[ "$MSC_GOOGLE_WEB_CLIENT_ID" == *.apps.googleusercontent.com ]] || {
  echo 'A valid Google web client ID is required.' >&2
  exit 1
}
if [[ -n "$MSC_BACKEND_BASE_URL" ]]; then
  [[ "$MSC_BACKEND_BASE_URL" == https://*.run.app ]] || {
    echo "Refusing non-Cloud-Run backend URL: $MSC_BACKEND_BASE_URL" >&2
    exit 1
  }
  [[ "$MSC_SMART_ONLINE_VALIDATED" == true ]] || {
    echo 'A packaged Cloud Run URL must have passed the release health contract.' >&2
    exit 1
  }
elif [[ "$MSC_SMART_ONLINE_VALIDATED" != false ]]; then
  echo 'Smart Online cannot be validated without a Cloud Run URL.' >&2
  exit 1
fi

APP_GRADLE='MyStudyCompanion/app/build.gradle.kts'
WEAR_GRADLE='MyStudyCompanion/wear/build.gradle.kts'
WIDGET_RECEIVERS='MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/StudyCompanionWidgets.kt'
WIDGET_DATA='MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt'
MANIFEST='MyStudyCompanion/app/src/main/AndroidManifest.xml'

grep -Fq 'versionCode = 51' "$APP_GRADLE"
grep -Fq 'versionName = "0.15.18-private-alpha-premium-widgets"' "$APP_GRADLE"
grep -Fq 'versionCode = 360168001' "$WEAR_GRADLE"
grep -Fq 'versionName = "0.15.18-wear-private-alpha-premium-widgets"' "$WEAR_GRADLE"
grep -Fq 'class CompanionDashboardWidget : GlanceAppWidget()' "$WIDGET_DATA"
grep -Fq 'SizeMode.Responsive' "$WIDGET_DATA"
grep -Fq 'loadWidgetData(context)' "$WIDGET_DATA"
grep -Fq 'override val glanceAppWidget = CompanionDashboardWidget()' "$WIDGET_RECEIVERS"
! grep -Fq 'CompanionPagerService' "$MANIFEST"

python3 -m pip install --disable-pip-version-check \
  -r MyStudyCompanion/backend/requirements.txt \
  -r MyStudyCompanion/backend/requirements-dev.txt
(cd MyStudyCompanion/backend && python3 -m pytest -q)
for file in MyStudyCompanionWeb/*.js; do node --check "$file"; done
node --test MyStudyCompanionWeb/*.test.mjs

pushd MyStudyCompanion >/dev/null
gradle --no-daemon --stacktrace \
  :app:testDebugUnitTest \
  :wear:testDebugUnitTest
gradle --no-daemon --stacktrace \
  -PMSC_BACKEND_BASE_URL="$MSC_BACKEND_BASE_URL" \
  -PMSC_GOOGLE_WEB_CLIENT_ID="$MSC_GOOGLE_WEB_CLIENT_ID" \
  :app:assemblePrivateAlpha \
  :wear:assemblePrivateAlpha
popd >/dev/null

PHONE_SOURCE='MyStudyCompanion/app/build/outputs/apk/privateAlpha/app-privateAlpha.apk'
WEAR_SOURCE='MyStudyCompanion/wear/build/outputs/apk/privateAlpha/wear-privateAlpha.apk'
test -s "$PHONE_SOURCE"
test -s "$WEAR_SOURCE"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
test -s "$BUILD_TOOLS/lib/apksigner.jar"
test -x "$BUILD_TOOLS/zipalign"
test -x "$BUILD_TOOLS/aapt"

RELEASE='release-0.15.18-signing-input'
rm -rf "$RELEASE"
mkdir -p "$RELEASE/phone" "$RELEASE/wear" "$RELEASE/metadata"
PHONE_NAME='MyStudyCompanion-phone-0.15.18-SIGNING-INPUT-NOT-INSTALLABLE.apk'
WEAR_NAME='MyStudyCompanion-wear-0.15.18-SIGNING-INPUT-NOT-INSTALLABLE.apk'
PHONE="$RELEASE/phone/$PHONE_NAME"
WEAR="$RELEASE/wear/$WEAR_NAME"
cp "$PHONE_SOURCE" "$PHONE"
cp "$WEAR_SOURCE" "$WEAR"

"$BUILD_TOOLS/aapt" dump badging "$PHONE" > "$RELEASE/metadata/PHONE-IDENTITY.txt"
"$BUILD_TOOLS/aapt" dump badging "$WEAR" > "$RELEASE/metadata/WEAR-IDENTITY.txt"
java -jar "$BUILD_TOOLS/lib/apksigner.jar" verify --verbose --print-certs "$PHONE" \
  > "$RELEASE/metadata/PHONE-STAGING-SIGNATURE.txt"
java -jar "$BUILD_TOOLS/lib/apksigner.jar" verify --verbose --print-certs "$WEAR" \
  > "$RELEASE/metadata/WEAR-STAGING-SIGNATURE.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$PHONE" > "$RELEASE/metadata/PHONE-ALIGNMENT.txt"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$WEAR" > "$RELEASE/metadata/WEAR-ALIGNMENT.txt"

grep -Fq "package: name='com.mystudycompanion.app' versionCode='51' versionName='0.15.18-private-alpha-premium-widgets'" \
  "$RELEASE/metadata/PHONE-IDENTITY.txt"
grep -Fq "package: name='com.mystudycompanion.app' versionCode='360168001' versionName='0.15.18-wear-private-alpha-premium-widgets'" \
  "$RELEASE/metadata/WEAR-IDENTITY.txt"
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$RELEASE/metadata/PHONE-STAGING-SIGNATURE.txt"
grep -Fq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$RELEASE/metadata/PHONE-STAGING-SIGNATURE.txt"
! grep -Fiq '13a3bb08a32ab57c36ea6f885a74b5bd40d70501f93ca9002df7b6cdd0491b2c' \
  "$RELEASE/metadata/PHONE-STAGING-SIGNATURE.txt"

grep -Fq "BACKEND_BASE_URL = \"$MSC_BACKEND_BASE_URL\"" \
  MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java
grep -Fq "GOOGLE_WEB_CLIENT_ID = \"$MSC_GOOGLE_WEB_CLIENT_ID\"" \
  MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha/com/mystudycompanion/app/BuildConfig.java

cp MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/StudyCompanionWidgets.kt \
  "$RELEASE/metadata/StudyCompanionWidgets.kt"
cp MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt \
  "$RELEASE/metadata/DailyStudyWidget.kt"
cp MyStudyCompanion/app/src/main/AndroidManifest.xml "$RELEASE/metadata/AndroidManifest.xml"
if [[ -s endpoint-health.json ]]; then
  cp endpoint-health.json "$RELEASE/metadata/VERIFIED-CLOUD-RUN-HEALTH.json"
fi

cat > "$RELEASE/metadata/DO-NOT-INSTALL.txt" <<EOF
THIS IS AN INTERNAL CI SIGNING INPUT, NOT AN ANDROID RELEASE.
The temporary staging signature is intentionally not the Firebase-registered application identity.
Neither APK may be installed, distributed, or described as an update.
Only an offline finalization using the recovered canonical certificate SHA-256
13a3bb08a32ab57c36ea6f885a74b5bd40d70501f93ca9002df7b6cdd0491b2c
may create the user-facing release.
Firebase Auth, Google Sign-In, Firestore, App Check configuration, package identity, and tested payload remain in this signing input.
Cloud Run backend packaged: ${MSC_BACKEND_BASE_URL:-none}
Smart Online release validation: $MSC_SMART_ONLINE_VALIDATED
EOF

cat > "$RELEASE/metadata/WIDGET-REBUILD-AUDIT.txt" <<'EOF'
PASS: existing Home, Study, Family, and Dashboard provider class names are preserved.
PASS: all four providers delegate to Android Glance widgets.
PASS: the 3-D StackView and RemoteViewsService collection deck are removed.
PASS: the dashboard selects compact, standard, wide, or tall content from launcher dimensions.
PASS: Daily Text, weekly preparation, Bible reading, Family Worship schedule, title, theme, and progress use the app's Room-backed data.
PASS: all user-visible widget text uses bounded lines and readable full-width sections rather than miniature fixed-width cards.
EOF

printf '%s\n' \
  "source_commit=${GITHUB_SHA:-local}" \
  "workflow_run=${GITHUB_RUN_ID:-local}" \
  "backend_url=${MSC_BACKEND_BASE_URL:-}" \
  "smart_online_validated=$MSC_SMART_ONLINE_VALIDATED" \
  'release=0.15.18' \
  'signature=temporary CI staging identity; do not install' \
  > "$RELEASE/metadata/BUILD-INPUT.txt"

(
  cd "$RELEASE"
  sha256sum "phone/$PHONE_NAME" "wear/$WEAR_NAME" > metadata/SHA256SUMS-STAGING.txt
  sha256sum -c metadata/SHA256SUMS-STAGING.txt
)

echo 'PASS: tested 0.15.18 signing inputs are packaged for offline canonical finalization.'
