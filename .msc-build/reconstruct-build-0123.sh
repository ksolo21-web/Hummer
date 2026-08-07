#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0122.sh').read_text(encoding='utf-8')
anchor = "python3 .msc-build/patch-0.12.2-final-test-imports.py\n"
if source.count(anchor) != 1:
    raise SystemExit('Expected one 0.12.2 final-test-imports anchor.')
addition = r'''python3 .msc-build/patch-0.12.2-final-test-imports.py
cat .msc-build/firebase-family-0.12.3.part*.b64 | base64 --decode > /tmp/firebase-family-0.12.3-overlay.tar.xz
echo 'fc5d7909d3f739e6eb33d95f56c17e14013f0ffb6f685f7b78a9cea59a3cc8a2  /tmp/firebase-family-0.12.3-overlay.tar.xz' | sha256sum -c -
tar -xJf /tmp/firebase-family-0.12.3-overlay.tar.xz -C MyStudyCompanion
grep -q 'versionCode = 27' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.3-private-alpha-firebase-family' MyStudyCompanion/app/build.gradle.kts
grep -q 'firebase-firestore' MyStudyCompanion/gradle/libs.versions.toml
grep -q 'FirebaseFirestore' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
! grep -q 'BackendApi' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
test -s MyStudyCompanion/firestore.rules
'''
source = source.replace(anchor, addition, 1)

replacements = {
    '"grep -q \'versionCode = 25\'": "grep -q \'versionCode = 26\'"':
        '"grep -q \'versionCode = 25\'": "grep -q \'versionCode = 27\'"',
    '"grep -q \'0.12.1-private-alpha-grounded-links\'": "grep -q \'0.12.2-private-alpha-complete-jw-links\'"':
        '"grep -q \'0.12.1-private-alpha-grounded-links\'": "grep -q \'0.12.3-private-alpha-firebase-family\'"',
    '"versionCode=\'25\'": "versionCode=\'26\'"':
        '"versionCode=\'25\'": "versionCode=\'27\'"',
    '"versionName=\'0.12.1-private-alpha-grounded-links-debug\'": "versionName=\'0.12.2-private-alpha-complete-jw-links-debug\'"':
        '"versionName=\'0.12.1-private-alpha-grounded-links-debug\'": "versionName=\'0.12.3-private-alpha-firebase-family-debug\'"',
    '"MyStudyCompanion-phone-0.12.1-debug.apk": "MyStudyCompanion-phone-0.12.2-debug.apk"':
        '"MyStudyCompanion-phone-0.12.1-debug.apk": "MyStudyCompanion-phone-0.12.3-debug.apk"',
}
for old, new in replacements.items():
    if source.count(old) != 1:
        raise SystemExit(f'Missing one 0.12.3 driver replacement anchor: {old}')
    source = source.replace(old, new, 1)

source = source.replace(
    "Path('/tmp/reconstruct-build-0122-generated.sh').write_text(source, encoding='utf-8')",
    "Path('/tmp/reconstruct-build-0123-generated.sh').write_text(source, encoding='utf-8')",
    1,
)
source = source.replace(
    'exec bash /tmp/reconstruct-build-0122-generated.sh',
    'exec bash /tmp/reconstruct-build-0123-generated.sh',
    1,
)
Path('/tmp/reconstruct-build-0123-driver.py').write_text(source, encoding='utf-8')
PY

exec bash /tmp/reconstruct-build-0123-driver.py
