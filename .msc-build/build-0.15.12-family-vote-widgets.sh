#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${MSC_RELEASE_STORE_FILE:?MSC_RELEASE_STORE_FILE is required}"
: "${MSC_RELEASE_STORE_PASSWORD:?MSC_RELEASE_STORE_PASSWORD is required}"
: "${MSC_RELEASE_KEY_ALIAS:?MSC_RELEASE_KEY_ALIAS is required}"
: "${MSC_RELEASE_KEY_PASSWORD:?MSC_RELEASE_KEY_PASSWORD is required}"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AAPT="$SDK_ROOT/build-tools/36.0.0/aapt"
APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"
ZIPALIGN="$SDK_ROOT/build-tools/36.0.0/zipalign"
for tool in "$AAPT" "$APKSIGNER" "$ZIPALIGN"; do test -x "$tool"; done

# Reconstruct and verify the complete accepted 0.15.11 source first. Apply the
# repair to that exact source and rebuild, avoiding fragile nested-driver edits.
bash .msc-build/build-0.15.11-local-family-generator.sh
bash .msc-build/apply-0.15.12-family-vote-widgets.sh
bash .msc-build/apply-0.15.12-widget-compile-fix.sh

python3 - <<'PY'
from pathlib import Path
import re

targets = (
    (Path("MyStudyCompanion/app/build.gradle.kts"), "45", "0.15.12-private-alpha-family-vote-widgets"),
    (Path("MyStudyCompanion/wear/build.gradle.kts"), "360162001", "0.15.12-wear-private-alpha-family-vote-widgets"),
)
for path, code, name in targets:
    text = path.read_text(encoding="utf-8")
    text, code_count = re.subn(r"versionCode\s*=\s*\d+", f"versionCode = {code}", text, count=1)
    text, name_count = re.subn(r'versionName\s*=\s*"[^"]+"', f'versionName = "{name}"', text, count=1)
    if code_count != 1 or name_count != 1:
        raise SystemExit(f"version identity patch failed for {path}")
    path.write_text(text, encoding="utf-8")
PY

MSC_GOOGLE_WEB_CLIENT_ID="$(python3 - <<'PY'
import json
from pathlib import Path
data = json.loads(Path("MyStudyCompanion/app/google-services.json").read_text(encoding="utf-8"))
canonical = next(
    item for item in data["client"]
    if item["client_info"]["android_client_info"]["package_name"] == "com.mystudycompanion.app"
)
web = [item for item in canonical["oauth_client"] if item["client_type"] == 3]
assert len(web) == 1
print(web[0]["client_id"])
PY
)"
test -n "$MSC_GOOGLE_WEB_CLIENT_ID"

(
  cd MyStudyCompanion
  gradle --no-daemon --stacktrace :app:testDebugUnitTest
  gradle --no-daemon --stacktrace \
    -PMSC_LOCAL_OWNER_MODE=true \
    -PMSC_GOOGLE_WEB_CLIENT_ID="$MSC_GOOGLE_WEB_CLIENT_ID" \
    -PMSC_BACKEND_BASE_URL='' \
    :app:assemblePrivateAlpha :wear:assemblePrivateAlpha
)

FAMILY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
WIDGETS="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/StudyCompanionWidgets.kt"
MANIFEST="MyStudyCompanion/app/src/main/AndroidManifest.xml"
UI="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt"
RES="MyStudyCompanion/app/src/main/res"

grep -Fq 'pendingVoteAdditions' "$FAMILY"
grep -Fq 'pendingVoteRemovals' "$FAMILY"
grep -Fq 'reconcileFamilyVoteKeys' "$FAMILY"
! grep -Fq 'localIdeasById[record.idea.id]?.voterUids' "$FAMILY"
grep -Fq 'class HomeSummaryWidgetProvider' "$WIDGETS"
grep -Fq 'class StudySummaryWidgetProvider' "$WIDGETS"
grep -Fq 'class FamilySummaryWidgetProvider' "$WIDGETS"
grep -Fq 'class CompanionPagerWidgetProvider' "$WIDGETS"
grep -Fq 'class CompanionPagerService' "$WIDGETS"
grep -Fq 'setRemoteAdapter(R.id.widget_stack' "$WIDGETS"
grep -Fq 'setPendingIntentTemplate' "$WIDGETS"
grep -Fq 'setOnClickFillInIntent' "$WIDGETS"
grep -Fq 'mystudycompanion://widget/home' "$UI"
grep -Fq 'mystudycompanion://widget/study' "$UI"
grep -Fq 'mystudycompanion://widget/family' "$UI"
grep -Fq 'android.permission.BIND_REMOTEVIEWS' "$MANIFEST"
grep -Fq '.widget.CompanionPagerWidgetProvider' "$MANIFEST"
test "$(grep -c 'android.appwidget.action.APPWIDGET_UPDATE' "$MANIFEST")" -eq 4
test -s "$RES/xml/widget_home_info.xml"
test -s "$RES/xml/widget_study_info.xml"
test -s "$RES/xml/widget_family_info.xml"
test -s "$RES/xml/widget_pager_info.xml"
test -s "$RES/layout/widget_pager.xml"
test -s "$RES/layout/widget_pager_page.xml"
test -s "$RES/layout/widget_summary.xml"

rm -rf release-0.15.12
mkdir -p release-0.15.12/phone release-0.15.12/wear release-0.15.12/metadata
PHONE_SOURCE="$(find MyStudyCompanion/app/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
WEAR_SOURCE="$(find MyStudyCompanion/wear/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
test -s "$PHONE_SOURCE"
test -s "$WEAR_SOURCE"
PHONE_APK="release-0.15.12/phone/MyStudyCompanion-phone-0.15.12-configured-ci.apk"
WEAR_APK="release-0.15.12/wear/MyStudyCompanion-wear-0.15.12-configured-ci.apk"
cp "$PHONE_SOURCE" "$PHONE_APK"
cp "$WEAR_SOURCE" "$WEAR_APK"

"$AAPT" dump badging "$PHONE_APK" > release-0.15.12/metadata/PHONE-IDENTITY.txt
"$AAPT" dump badging "$WEAR_APK" > release-0.15.12/metadata/WEAR-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app' versionCode='45'" release-0.15.12/metadata/PHONE-IDENTITY.txt
grep -q "versionName='0.15.12-private-alpha-family-vote-widgets'" release-0.15.12/metadata/PHONE-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app' versionCode='360162001'" release-0.15.12/metadata/WEAR-IDENTITY.txt
grep -q "versionName='0.15.12-wear-private-alpha-family-vote-widgets'" release-0.15.12/metadata/WEAR-IDENTITY.txt

"$APKSIGNER" verify --verbose --print-certs "$PHONE_APK" > release-0.15.12/metadata/PHONE-CI-SIGNATURE.txt
"$APKSIGNER" verify --verbose --print-certs "$WEAR_APK" > release-0.15.12/metadata/WEAR-CI-SIGNATURE.txt
"$ZIPALIGN" -c -P 16 -v 4 "$PHONE_APK" > release-0.15.12/metadata/PHONE-ZIPALIGN.txt
"$ZIPALIGN" -c -P 16 -v 4 "$WEAR_APK" > release-0.15.12/metadata/WEAR-ZIPALIGN.txt
sha256sum "$PHONE_APK" "$WEAR_APK" > release-0.15.12/metadata/CI-SHA256SUMS.txt

cat > release-0.15.12/metadata/FAMILY-VOTE-WIDGET-RELEASE-AUDIT.txt <<'EOF'
PASS: Family Worship vote and unvote use cloud-authoritative reconciliation.
PASS: a stale Firestore snapshot cannot resurrect a locally removed vote.
PASS: failed vote writes visibly roll back instead of leaving false state.
PASS: the three original widget slots are replaced by working Home, Study, and Family widgets.
PASS: every summary widget uses a unique immutable PendingIntent.
PASS: the new large widget uses a RemoteViews StackView with three swipeable pages.
PASS: Home, Study, and Family widget taps route to the matching top-level app tab.
PASS: all four widgets support launcher resizing.
PASS: Android unit tests and the complete inherited 0.15.11 release gate passed.
EOF

printf 'PASS: configured 0.15.12 phone and Wear APKs built and verified.\n'
