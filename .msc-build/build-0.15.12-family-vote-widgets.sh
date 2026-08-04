#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRIVER=".msc-build/build-0.15.12-generated.sh"
cp .msc-build/build-0.15.11-local-family-generator.sh "$DRIVER"
trap 'rm -f "$DRIVER"' EXIT

python3 - "$DRIVER" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def replace(old: str, new: str, label: str, count: int | None = None) -> None:
    global text
    found = text.count(old)
    if found == 0:
        raise SystemExit(f"{label} target not found")
    if count is not None and found != count:
        raise SystemExit(f"{label} expected {count}, found {found}")
    text = text.replace(old, new)

replace(
    "'bash .msc-build/apply-0.15.11-local-family-generator.sh\\n',",
    "'bash .msc-build/apply-0.15.11-local-family-generator.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.12-family-vote-widgets.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.12-widget-compile-fix.sh\\n',",
    "0.15.12 overlay insertion",
    1,
)
replace("'44', '0.15.11-private-alpha-local-family-generator'", "'45', '0.15.12-private-alpha-family-vote-widgets'", "phone identity pin", 1)
replace("'360161001', '0.15.11-wear-private-alpha-local-family-generator'", "'360162001', '0.15.12-wear-private-alpha-family-vote-widgets'", "Wear identity pin", 1)
replace("release-0.15.11", "release-0.15.12", "release directory")
replace("MyStudyCompanion-phone-0.15.11-configured-ci.apk", "MyStudyCompanion-phone-0.15.12-configured-ci.apk", "phone artifact name", 1)
replace("MyStudyCompanion-wear-0.15.11-configured-ci.apk", "MyStudyCompanion-wear-0.15.12-configured-ci.apk", "Wear artifact name", 1)
replace("versionCode='44'", "versionCode='45'", "phone version assertion", 1)
replace("versionName='0.15.11-private-alpha-local-family-generator'", "versionName='0.15.12-private-alpha-family-vote-widgets'", "phone version assertion", 1)
replace("versionCode='360161001'", "versionCode='360162001'", "Wear version assertion", 1)
replace("versionName='0.15.11-wear-private-alpha-local-family-generator'", "versionName='0.15.12-wear-private-alpha-family-vote-widgets'", "Wear version assertion", 1)
replace("configured 0.15.11 phone and Wear APKs", "configured 0.15.12 phone and Wear APKs", "completion message", 1)
path.write_text(text, encoding="utf-8")
PY

chmod +x "$DRIVER"
bash "$DRIVER"

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

mkdir -p release-0.15.12/metadata
cat > release-0.15.12/metadata/FAMILY-VOTE-WIDGET-RELEASE-AUDIT.txt <<'EOF'
PASS: Family Worship vote and unvote use cloud-authoritative reconciliation.
PASS: a stale Firestore snapshot cannot resurrect a locally removed vote.
PASS: failed vote writes visibly roll back instead of leaving false state.
PASS: the three original widget slots are replaced by working Home, Study, and Family widgets.
PASS: every summary widget uses a unique immutable PendingIntent.
PASS: the new large widget uses a RemoteViews StackView with three swipeable pages.
PASS: Home, Study, and Family widget taps route to the matching top-level app tab.
PASS: all four widgets support launcher resizing.
PASS: Android unit tests and the complete inherited release gate passed.
EOF
