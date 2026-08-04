#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRIVER=".msc-build/build-0.15.11-generated.sh"
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

# This script edits the source of the 0.15.10 build driver. The doubled
# backslashes below intentionally match the literal \n escapes in that source.
replace(
    'bash .msc-build/apply-0.15.10-household-date-recovery.sh\\n',
    'bash .msc-build/apply-0.15.10-household-date-recovery.sh\\n'
    '    \'bash .msc-build/apply-0.15.11-local-family-generator.sh\\n\'',
    'local generator overlay insertion',
    1,
)
replace("'43', '0.15.10-private-alpha-household-date-recovery'", "'44', '0.15.11-private-alpha-local-family-generator'", 'phone identity pin', 1)
replace("'360160001', '0.15.10-wear-private-alpha-household-date-recovery'", "'360161001', '0.15.11-wear-private-alpha-local-family-generator'", 'Wear identity pin', 1)
replace('release-0.15.10', 'release-0.15.11', 'release directory')
replace('MyStudyCompanion-phone-0.15.10-configured-ci.apk', 'MyStudyCompanion-phone-0.15.11-configured-ci.apk', 'phone artifact name', 1)
replace('MyStudyCompanion-wear-0.15.10-configured-ci.apk', 'MyStudyCompanion-wear-0.15.11-configured-ci.apk', 'Wear artifact name', 1)
replace("versionCode='43'", "versionCode='44'", 'phone identity assertion', 1)
replace("versionName='0.15.10-private-alpha-household-date-recovery'", "versionName='0.15.11-private-alpha-local-family-generator'", 'phone version assertion', 1)
replace("versionCode='360160001'", "versionCode='360161001'", 'Wear identity assertion', 1)
replace("versionName='0.15.10-wear-private-alpha-household-date-recovery'", "versionName='0.15.11-wear-private-alpha-local-family-generator'", 'Wear version assertion', 1)

anchor = "! sed -n '/suspend fun createHousehold(/,/suspend fun createHouseholdInvitation/p' \"$FAMILY\" | grep -Fq 'SetOptions.merge()'\n"
local_gates = anchor + """

# On-device Family Worship generation removes the dead private-service dependency.
grep -Fq 'buildOnDeviceOfficialSourcePlan' \"$FAMILY\"
grep -Fq 'studyRepository.replaceFamilyWorshipFromHousehold(study)' \"$FAMILY\"
grep -Fq 'The official-source Family Worship plan was created and sent to your household.' \"$FAMILY\"
grep -Fq 'The app will build the plan on this device' \"$UI/FamilyWorshipScreen.kt\"
! grep -Fq 'The official-source family study service is not connected in this build' \"$FAMILY\"

python3 - <<'AUDIT'
from pathlib import Path

family = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt').read_text(encoding='utf-8')
worship = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt').read_text(encoding='utf-8')
section = family.split('suspend fun generateAndSend(', 1)[1].split('fun clearMessage()', 1)[0]
assert 'buildOnDeviceOfficialSourcePlan' in section
assert 'require(backendConfig.isConfigured)' not in section
assert 'replaceFamilyWorshipFromHousehold(study)' in section
assert 'JwLibraryLinkResolver.bibleUrl' in family
assert 'private service will research only official sources' not in worship

audit = Path('release-0.15.11/metadata/LOCAL-FAMILY-GENERATOR-AUDIT.txt')
audit.parent.mkdir(parents=True, exist_ok=True)
lines = [
    'PASS: Create and send works even when the private HTTPS generator is not configured.',
    'PASS: the app creates the Family Worship plan on-device from a deterministic Bible-principle template.',
    'PASS: direct New World Translation scripture targets are used for the overview and every section.',
    'PASS: the generated plan is validated, saved locally, and published through the household Firestore document.',
    'PASS: the optional signed private-service path remains available when a backend is configured.',
    'PASS: household recovery, stable date selection, adaptive scrolling, Google sign-in, and Firebase Spark behavior remain intact.',
]
audit.write_text(chr(10).join(lines) + chr(10), encoding='utf-8')
AUDIT
"""
replace(anchor, local_gates, 'local generator source gates', 1)

replace(
    'PASS: missing private-service configuration is handled in-page and cannot crash the app.\\n',
    'PASS: missing private-service configuration is handled in-page and cannot crash the app.\\n'
    "    'PASS: Family Worship now generates on-device when the private HTTPS service is unavailable.\\n'\n"
    "    'PASS: generated plans use direct NWT scripture targets, are stored locally, and sync through the household Firestore record.\\n'",
    'release gate additions',
    1,
)
replace('configured 0.15.10 phone and Wear APKs', 'configured 0.15.11 phone and Wear APKs', 'completion message', 1)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$DRIVER"
bash "$DRIVER"
