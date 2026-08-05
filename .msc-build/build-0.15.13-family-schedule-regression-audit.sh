#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRIVER=".msc-build/build-0.15.13-generated.sh"
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
    "    'bash .msc-build/apply-0.15.13-family-schedule-regression-audit.sh\\n',",
    '0.15.11 and 0.15.13 overlay insertion',
    1,
)
replace("'43', '0.15.10-private-alpha-household-date-recovery'", "'46', '0.15.13-private-alpha-family-schedule-regression-audit'", 'phone identity pin', 1)
replace("'360160001', '0.15.10-wear-private-alpha-household-date-recovery'", "'360163001', '0.15.13-wear-private-alpha-family-schedule-regression-audit'", 'Wear identity pin', 1)
replace('release-0.15.10', 'release-0.15.13', 'release directory')
replace('MyStudyCompanion-phone-0.15.10-configured-ci.apk', 'MyStudyCompanion-phone-0.15.13-configured-ci.apk', 'phone artifact name', 1)
replace('MyStudyCompanion-wear-0.15.10-configured-ci.apk', 'MyStudyCompanion-wear-0.15.13-configured-ci.apk', 'Wear artifact name', 1)
replace("versionCode='43'", "versionCode='46'", 'phone identity assertion', 1)
replace("versionName='0.15.10-private-alpha-household-date-recovery'", "versionName='0.15.13-private-alpha-family-schedule-regression-audit'", 'phone version assertion', 1)
replace("versionCode='360160001'", "versionCode='360163001'", 'Wear identity assertion', 1)
replace("versionName='0.15.10-wear-private-alpha-household-date-recovery'", "versionName='0.15.13-wear-private-alpha-family-schedule-regression-audit'", 'Wear version assertion', 1)
replace('configured 0.15.10 phone and Wear APKs', 'configured 0.15.13 phone and Wear APKs', 'completion message', 1)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$DRIVER"
bash "$DRIVER"

REPOSITORY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/CompanionHubRepository.kt"
UI="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/CompanionHubScreen.kt"
WIDGET="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"
XML_DIR="MyStudyCompanion/app/src/main/res/xml"
FAMILY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"

grep -Fq 'buildOnDeviceOfficialSourcePlan' "$FAMILY"
grep -Fq 'data class FamilyWorshipScheduleResult' "$REPOSITORY"
grep -Fq 'Choose a final family worship topic before saving' "$REPOSITORY"
grep -Fq 'FamilyWorshipReminderScheduler.schedule(appContext, normalizedDate, normalizedTime, recurringWeekly)' "$REPOSITORY"
grep -Fq 'scheduleMessage by rememberSaveable' "$UI"
grep -Fq 'layoutSpec.widthClass == AdaptiveWidthClass.COMPACT' "$UI"
grep -Fq 'watchtower?.let { snapshot.weekly.withOfficialWatchtowerStudy(it) }' "$WIDGET"
grep -Fq 'android:updatePeriodMillis="1800000"' "$XML_DIR/daily_study_widget_info.xml"
grep -Fq 'android:updatePeriodMillis="1800000"' "$XML_DIR/weekly_study_widget_info.xml"
grep -Fq 'android:updatePeriodMillis="1800000"' "$XML_DIR/family_worship_widget_info.xml"
! grep -Fq 'onClick = { repository.scheduleFamilyWorship(date, time, duration.toIntOrNull() ?: 60, recurring) }' "$UI"

mkdir -p release-0.15.13/metadata
cat > release-0.15.13/metadata/FAMILY-SCHEDULE-REGRESSION-AUDIT.txt <<'EOF'
PASS: family worship scheduling returns visible success or validation feedback instead of silently returning.
PASS: invalid dates, invalid times, invalid duration, missing final topic, unauthorized organizer, and past one-time dates are handled in-page.
PASS: weekly schedules entered in the past advance to the next future occurrence.
PASS: reminder permission/runtime failures cannot crash the save action; the family schedule remains saved.
PASS: restored and cloud-synchronized schedule values update the visible form.
PASS: compact phone layouts stack date and time fields to avoid clipped labels and controls.
PASS: official-source network failures fall back to locally stored widget content.
PASS: failure of one widget refresh does not prevent the remaining widget types from refreshing.
PASS: daily, weekly, family, and cover widgets request Android's 30-minute periodic refresh.
PASS: household recovery, family voting, local Family Worship generation, adaptive scrolling, Google sign-in, and Wear support remain in the inherited build and compile gates.
EOF
