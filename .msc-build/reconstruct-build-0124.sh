#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0123.sh').read_text(encoding='utf-8')

old_overlay_checks = r'''grep -q 'versionCode = 27' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.3-private-alpha-firebase-family' MyStudyCompanion/app/build.gradle.kts
grep -q 'firebase-firestore' MyStudyCompanion/gradle/libs.versions.toml
grep -q 'FirebaseFirestore' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
! grep -q 'BackendApi' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
test -s MyStudyCompanion/firestore.rules
'''
new_overlay_checks = r'''grep -q 'firebase-firestore' MyStudyCompanion/gradle/libs.versions.toml
grep -q 'FirebaseFirestore' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
! grep -q 'BackendApi' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
test -s MyStudyCompanion/firestore.rules
python3 .msc-build/patch-0.12.4-firestore-hardening.py
grep -q 'versionCode = 28' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.4-private-alpha-firebase-rules-hardened' MyStudyCompanion/app/build.gradle.kts
grep -q 'request.resource.data.householdId == resource.data.householdId' MyStudyCompanion/firestore.rules
grep -q 'request.resource.data.usedAt == request.time' MyStudyCompanion/firestore.rules
'''
if source.count(old_overlay_checks) != 1:
    raise SystemExit('Expected one 0.12.3 overlay verification block.')
source = source.replace(old_overlay_checks, new_overlay_checks, 1)

replacements = {
    '"grep -q \'versionCode = 25\'": "grep -q \'versionCode = 27\'"':
        '"grep -q \'versionCode = 25\'": "grep -q \'versionCode = 28\'"',
    '"grep -q \'0.12.1-private-alpha-grounded-links\'": "grep -q \'0.12.3-private-alpha-firebase-family\'"':
        '"grep -q \'0.12.1-private-alpha-grounded-links\'": "grep -q \'0.12.4-private-alpha-firebase-rules-hardened\'"',
    '"versionCode=\'25\'": "versionCode=\'27\'"':
        '"versionCode=\'25\'": "versionCode=\'28\'"',
    '"versionName=\'0.12.1-private-alpha-grounded-links-debug\'": "versionName=\'0.12.3-private-alpha-firebase-family-debug\'"':
        '"versionName=\'0.12.1-private-alpha-grounded-links-debug\'": "versionName=\'0.12.4-private-alpha-firebase-rules-hardened-debug\'"',
    '"MyStudyCompanion-phone-0.12.1-debug.apk": "MyStudyCompanion-phone-0.12.3-debug.apk"':
        '"MyStudyCompanion-phone-0.12.1-debug.apk": "MyStudyCompanion-phone-0.12.4-debug.apk"',
    "Path('/tmp/reconstruct-build-0123-driver.py').write_text(source, encoding='utf-8')":
        "Path('/tmp/reconstruct-build-0124-driver.py').write_text(source, encoding='utf-8')",
    'exec bash /tmp/reconstruct-build-0123-driver.py':
        'exec bash /tmp/reconstruct-build-0124-driver.py',
}
for old, new in replacements.items():
    if source.count(old) != 1:
        raise SystemExit(f'Missing one 0.12.4 driver replacement anchor: {old}')
    source = source.replace(old, new, 1)

Path('/tmp/reconstruct-build-0124-wrapper.sh').write_text(source, encoding='utf-8')
PY

exec bash /tmp/reconstruct-build-0124-wrapper.sh
