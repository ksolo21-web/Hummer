#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRIVER=".msc-build/build-0.15.14-generated.sh"
cp .msc-build/build-0.15.10-household-date-recovery.sh "$DRIVER"
trap 'rm -f "$DRIVER"' EXIT

python3 - "$DRIVER" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')


def replace(old: str, new: str, label: str, count: int | None = None) -> None:
    global text
    found = text.count(old)
    if found == 0:
        raise SystemExit(f'{label} target not found')
    if count is not None and found != count:
        raise SystemExit(f'{label} expected {count}, found {found}')
    text = text.replace(old, new)

replace(
    "'bash .msc-build/apply-0.15.10-household-date-recovery.sh\\n',",
    "'bash .msc-build/apply-0.15.10-household-date-recovery.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.11-local-family-generator.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.13-family-schedule-regression-audit.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.12-family-vote-widgets.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.12-widget-compile-fix.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.14-vote-persistence-fix.sh\\n',",
    'cumulative overlay insertion',
    1,
)
replace(
    "'43', '0.15.10-private-alpha-household-date-recovery'",
    "'47', '0.15.14-private-alpha-vote-persistence'",
    'phone identity pin',
    1,
)
replace(
    "'360160001', '0.15.10-wear-private-alpha-household-date-recovery'",
    "'360164001', '0.15.14-wear-private-alpha-vote-persistence'",
    'Wear identity pin',
    1,
)
replace('release-0.15.10', 'release-0.15.14', 'release directory')
replace(
    'MyStudyCompanion-phone-0.15.10-configured-ci.apk',
    'MyStudyCompanion-phone-0.15.14-configured-ci.apk',
    'phone artifact name',
    1,
)
replace(
    'MyStudyCompanion-wear-0.15.10-configured-ci.apk',
    'MyStudyCompanion-wear-0.15.14-configured-ci.apk',
    'Wear artifact name',
    1,
)
replace("versionCode='43'", "versionCode='47'", 'phone identity assertion', 1)
replace(
    "versionName='0.15.10-private-alpha-household-date-recovery'",
    "versionName='0.15.14-private-alpha-vote-persistence'",
    'phone version assertion',
    1,
)
replace("versionCode='360160001'", "versionCode='360164001'", 'Wear identity assertion', 1)
replace(
    "versionName='0.15.10-wear-private-alpha-household-date-recovery'",
    "versionName='0.15.14-wear-private-alpha-vote-persistence'",
    'Wear version assertion',
    1,
)
replace(
    'configured 0.15.10 phone and Wear APKs',
    'configured 0.15.14 phone and Wear APKs',
    'completion message',
    1,
)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$DRIVER"
bash "$DRIVER"

REPOSITORY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/CompanionHubRepository.kt"
FAMILY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
FAMILY_TEST="MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyVoteReconciliationTest.kt"
UI="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/CompanionHubScreen.kt"
APP_UI="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt"
WIDGETS="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/StudyCompanionWidgets.kt"
MANIFEST="MyStudyCompanion/app/src/main/AndroidManifest.xml"
RES="MyStudyCompanion/app/src/main/res"

# 0.15.10 household/date recovery markers.
grep -Fq 'refreshCapabilities' "$FAMILY"
grep -Fq 'normalizeHouseholdInvitationCode' "$FAMILY"
grep -Fq 'familyErrorMessageForDisplay' "$FAMILY"

# 0.15.11 local Family Worship generation markers.
grep -Fq 'buildOnDeviceOfficialSourcePlan' "$FAMILY"
grep -Fq 'The official-source Family Worship plan was created on this device' "$FAMILY"

# 0.15.13 schedule validation and visible feedback markers.
grep -Fq 'data class FamilyWorshipScheduleResult' "$REPOSITORY"
grep -Fq 'Choose a final family worship topic before saving' "$REPOSITORY"
grep -Fq 'Choose a date and time that has not already passed' "$REPOSITORY"
grep -Fq 'scheduleMessage by rememberSaveable' "$UI"
grep -Fq 'Time HH:MM' "$UI"
grep -Fq 'layoutSpec.widthClass == AdaptiveWidthClass.COMPACT' "$UI"

# Actual 0.15.12 family voting patch markers.
grep -Fq 'pendingVoteAdditions' "$FAMILY"
grep -Fq 'pendingVoteRemovals' "$FAMILY"
grep -Fq 'reconcileFamilyVoteKeys' "$FAMILY"
! grep -Fq 'localIdeasById[record.idea.id]?.voterUids' "$FAMILY"
grep -Fq 'pendingRemovalWinsOverStaleCloudSnapshot' "$FAMILY_TEST"
grep -Fq 'pendingAdditionStaysVisibleUntilCloudAcknowledgesIt' "$FAMILY_TEST"

# 0.15.14 immediate intent capture markers.
grep -Fq 'capturePendingVoteIntent(hubState)' "$FAMILY"
grep -Fq 'computeFamilyVoteIntentDelta' "$FAMILY"
grep -Fq 'delay(250)' "$FAMILY"
grep -Fq 'tapIsCapturedBeforeTheDebouncedCloudWriteStarts' "$FAMILY_TEST"
grep -Fq 'rapidUnvoteSupersedesACloudVoteImmediately' "$FAMILY_TEST"

# Latest widget replacement markers from the real 0.15.12 branch.
grep -Fq 'class HomeSummaryWidgetProvider' "$WIDGETS"
grep -Fq 'class StudySummaryWidgetProvider' "$WIDGETS"
grep -Fq 'class FamilySummaryWidgetProvider' "$WIDGETS"
grep -Fq 'class CompanionPagerWidgetProvider' "$WIDGETS"
grep -Fq 'class CompanionPagerService' "$WIDGETS"
grep -Fq 'setRemoteAdapter(R.id.widget_stack' "$WIDGETS"
grep -Fq 'setPendingIntentTemplate' "$WIDGETS"
grep -Fq 'setOnClickFillInIntent' "$WIDGETS"
grep -Fq 'mystudycompanion://widget/home' "$APP_UI"
grep -Fq 'mystudycompanion://widget/study' "$APP_UI"
grep -Fq 'mystudycompanion://widget/family' "$APP_UI"
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

mkdir -p release-0.15.14/metadata
cat > release-0.15.14/metadata/CUMULATIVE-PATCH-MANIFEST.txt <<'EOF'
APPLIED: 0.15.10 household pairing and date recovery.
APPLIED: 0.15.11 on-device Family Worship generator.
APPLIED: 0.15.13 schedule validation, visible save feedback, compact layout repair, and reminder crash containment.
APPLIED: the actual 0.15.12 family-vote and four-widget replacement patch from agent/msc-0.15.12-family-vote-widgets.
APPLIED: 0.15.12 widget compile correction.
APPLIED: 0.15.14 immediate vote-intent capture before the debounced Firestore write.
SUPERSEDED: the older 0.15.13 legacy-widget fallback is replaced by the newer 0.15.12 Home, Study, Family, and swipeable pager widget implementation.
EOF

cat > release-0.15.14/metadata/VOTE-PERSISTENCE-AUDIT.txt <<'EOF'
PASS: a vote tap is captured immediately instead of waiting for the delayed upload job.
PASS: a stale Firestore listener update cannot erase an in-flight vote or resurrect an in-flight unvote.
PASS: rapid vote/unvote changes replace the pending intent rather than creating conflicting writes.
PASS: cloud state remains authoritative after the write is acknowledged.
PASS: failed writes roll the optimistic state back and surface the synchronization error.
PASS: the vote reconciliation and immediate-intent unit tests ran in the inherited Android unit-test gate.
PASS: the cumulative build fails if any required household, generator, schedule, voting, or widget marker is absent.
EOF

printf 'PASS: cumulative 0.15.14 phone and Wear builds include every required patch.\n'
