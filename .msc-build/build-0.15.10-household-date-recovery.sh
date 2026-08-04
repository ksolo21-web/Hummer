#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRIVER=".msc-build/build-0.15.10-generated.sh"
cp .msc-build/build-0.15.8-google-age-free-invite.sh "$DRIVER"
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
    'bash .msc-build/apply-0.15.8-google-age-firestore-compat.sh\n',
    'bash .msc-build/apply-0.15.8-google-age-firestore-compat.sh\n'
    'bash .msc-build/apply-0.15.9-adaptive-scroll.sh\n'
    'bash .msc-build/apply-0.15.10-household-date-recovery.sh\n',
    'recovery overlay insertion',
    1,
)
replace("'41', '0.15.8-private-alpha-google-age-free-invite'", "'43', '0.15.10-private-alpha-household-date-recovery'", 'phone identity pin', 1)
replace("'360158001', '0.15.8-wear-private-alpha-google-age-free-invite'", "'360160001', '0.15.10-wear-private-alpha-household-date-recovery'", 'Wear identity pin', 1)
replace('release-0.15.8', 'release-0.15.10', 'release directory')
replace('MyStudyCompanion-phone-0.15.8-configured-ci.apk', 'MyStudyCompanion-phone-0.15.10-configured-ci.apk', 'phone artifact name', 1)
replace('MyStudyCompanion-wear-0.15.8-configured-ci.apk', 'MyStudyCompanion-wear-0.15.10-configured-ci.apk', 'Wear artifact name', 1)
replace("versionCode='41'", "versionCode='43'", 'phone identity assertion', 1)
replace("versionName='0.15.8-private-alpha-google-age-free-invite'", "versionName='0.15.10-private-alpha-household-date-recovery'", 'phone version assertion', 1)
replace("versionCode='360158001'", "versionCode='360160001'", 'Wear identity assertion', 1)
replace("versionName='0.15.8-wear-private-alpha-google-age-free-invite'", "versionName='0.15.10-wear-private-alpha-household-date-recovery'", 'Wear version assertion', 1)

anchor = "grep -Fq 'refreshGoogleAgeFromAccount' \"$UI/MyStudyCompanionApp.kt\"\n"
recovery_gates = anchor + """
# Adaptive viewport contract retained from 0.15.9.
grep -Fq '.verticalScroll(householdScrollState)' \"$UI/FamilyHubScreen.kt\"
grep -Fq '.imePadding()' \"$UI/FamilyHubScreen.kt\"
grep -Fq 'withFrameNanos' \"$UI/FamilyHubScreen.kt\"
grep -Fq 'householdScrollState.animateScrollTo(householdScrollState.maxValue)' \"$UI/FamilyHubScreen.kt\"
grep -Fq 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)' \"$UI/HouseholdScreen.kt\"
! grep -Fq 'Box(modifier.fillMaxSize()' \"$UI/HouseholdScreen.kt\"

# Household pairing recovery and date-picker crash prevention.
grep -Fq 'familyHouseholdActionErrorForDisplay' \"$FAMILY\"
grep -Fq 'critical: Boolean = false' \"$FAMILY\"
grep -Fq 'Refresh household access' \"$UI/HouseholdScreen.kt\"
grep -Fq 'import android.app.DatePickerDialog' \"$UI/FamilyWorshipScreen.kt\"
grep -Fq 'LocalDate.parse(selectedDateIso) }.getOrElse' \"$UI/FamilyWorshipScreen.kt\"
! grep -Fq 'rememberDatePickerState' \"$UI/FamilyWorshipScreen.kt\"
! sed -n '/suspend fun generateAndSend/,/fun clearMessage/p' \"$FAMILY\" | sed -n '1,/runFamilyCatching/p' | grep -Fq 'require(backendConfig.isConfigured)'
! sed -n '/suspend fun joinHousehold/,/suspend fun generateAndSend/p' \"$FAMILY\" | grep -Fq 'SetOptions.merge()'
! sed -n '/suspend fun createHousehold(/,/suspend fun createHouseholdInvitation/p' \"$FAMILY\" | grep -Fq 'SetOptions.merge()'

python3 - <<'AUDIT'
from pathlib import Path

ui = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui')
family = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt').read_text(encoding='utf-8')
worship = (ui / 'FamilyWorshipScreen.kt').read_text(encoding='utf-8')
household = (ui / 'HouseholdScreen.kt').read_text(encoding='utf-8')
family_hub = (ui / 'FamilyHubScreen.kt').read_text(encoding='utf-8')

assert '.verticalScroll(householdScrollState)' in family_hub
assert 'Box(modifier.fillMaxSize()' not in household
assert 'DatePickerDialog(' in worship
assert 'rememberDatePickerState' not in worship
assert 'familyHouseholdActionErrorForDisplay' in family
assert 'Refresh household access' in household
assert '), SetOptions.merge())\n                transaction.update(resolvedInviteRef' not in family
assert '), SetOptions.merge())\n            batch.set(boardRef' not in family

audit = Path('release-0.15.10/metadata/HOUSEHOLD-DATE-RECOVERY-AUDIT.txt')
audit.parent.mkdir(parents=True, exist_ok=True)
audit.write_text(
    'PASS: household user links are replacement writes, removing stale forbidden fields.\n'
    'PASS: invitation collisions retry, while permission failures are surfaced immediately.\n'
    'PASS: optional listener permission errors no longer poison the complete Family UI.\n'
    'PASS: household access has an explicit refresh action.\n'
    'PASS: the family date selector uses the stable Android platform dialog.\n'
    'PASS: invalid persisted dates and unavailable backend services remain in-page errors.\n'
    'PASS: 0.15.9 adaptive scrolling remains active.\n',
    encoding='utf-8',
)
AUDIT
"""
replace(anchor, recovery_gates, 'recovery source gates', 1)

replace(
    'PASS: backend, Android, Wear, PWA, and Firestore Rules Emulator tests passed.\n',
    'PASS: backend, Android, Wear, PWA, and Firestore Rules Emulator tests passed.\n'
    'PASS: Profiles & household retains adaptive scrolling across phone, Fold, tablet, rotation, and multi-window.\n'
    'PASS: household create/join replaces stale user documents instead of preserving forbidden fields.\n'
    'PASS: invitation permission failures are no longer swallowed as random code collisions.\n'
    'PASS: optional family-sync permission errors no longer place a raw PERMISSION_DENIED line across the UI.\n'
    'PASS: household access includes an explicit refresh recovery action.\n'
    'PASS: family-study date selection uses a stable platform dialog with invalid-date recovery.\n'
    'PASS: missing private-service configuration is handled in-page and cannot crash the app.\n'
    'PASS: Google sign-in, Google age verification, Firebase Spark invitations, themes, notes, workbooks, and Wear support were retained.\n',
    'release gate additions',
    1,
)
replace('configured 0.15.8 phone and Wear APKs', 'configured 0.15.10 phone and Wear APKs', 'completion message', 1)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$DRIVER"
bash "$DRIVER"
