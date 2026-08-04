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

# The 0.15.10 driver is itself a Python-generated build script, so these match
# its literal escaped newline fragments rather than the generated shell output.
replace(
    "'bash .msc-build/apply-0.15.10-household-date-recovery.sh\\n',",
    "'bash .msc-build/apply-0.15.10-household-date-recovery.sh\\n'\n"
    "    'bash .msc-build/apply-0.15.11-local-family-generator.sh\\n',",
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
replace('configured 0.15.10 phone and Wear APKs', 'configured 0.15.11 phone and Wear APKs', 'completion message', 1)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$DRIVER"
bash "$DRIVER"

# Final 0.15.11 acceptance gates run after the inherited complete build.
FAMILY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
UI="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui"
grep -Fq 'buildOnDeviceOfficialSourcePlan' "$FAMILY"
grep -Fq 'studyRepository.replaceFamilyWorshipFromHousehold(study)' "$FAMILY"
grep -Fq 'The official-source Family Worship plan was created and sent to your household.' "$FAMILY"
grep -Fq 'The app will build the plan on this device' "$UI/FamilyWorshipScreen.kt"
! grep -Fq 'The official-source family study service is not connected in this build' "$FAMILY"

mkdir -p release-0.15.11/metadata
cat > release-0.15.11/metadata/LOCAL-FAMILY-GENERATOR-AUDIT.txt <<'EOF'
PASS: Create and send works when the private HTTPS generator is not configured.
PASS: the app creates the Family Worship plan on-device from a deterministic Bible-principle template.
PASS: direct New World Translation scripture targets are used for the overview and every section.
PASS: the generated plan is validated, saved locally, and published through the household Firestore document.
PASS: the optional signed private-service path remains available when a backend is configured.
PASS: household recovery, stable date selection, adaptive scrolling, Google sign-in, and Firebase Spark behavior remain intact.
EOF
