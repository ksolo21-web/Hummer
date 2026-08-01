#!/usr/bin/env bash
set -euo pipefail

base64 --decode .msc-build/live-release-completion-0.14.1.patch.xz.b64 \
  > /tmp/msc-live-release-completion-0.14.1.patch.xz
echo '945621c73fe28aa06ba30c658fdf3d1bf9d565065abd684ec3e11579e3c82b7e  /tmp/msc-live-release-completion-0.14.1.patch.xz' \
  | sha256sum -c -
xz -t /tmp/msc-live-release-completion-0.14.1.patch.xz
xz -dc /tmp/msc-live-release-completion-0.14.1.patch.xz \
  > /tmp/msc-live-release-completion-0.14.1.patch
patch --batch --forward -p1 < /tmp/msc-live-release-completion-0.14.1.patch

python3 - <<'PY'
from pathlib import Path

root = Path('.')
organizer = (root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt').read_text()
assert 'backendApi.generateFamilyWorship' in organizer
assert 'contentSyncEngine.sync("family_worship_generated")' in organizer
assert 'FamilyWorshipPublicationValidator.requirePublishable' in organizer
assert 'Family Worship plan selected by the household organizer.' not in organizer
assert 'val template = studyRepository.familyWorshipSnapshot()' not in organizer

container = (root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/AppContainer.kt').read_text()
for marker in ('backendConfig = backendConfig', 'backendApi = backendApi', 'contentSyncEngine = contentSyncEngine'):
    assert marker in container, marker

backend = root / 'MyStudyCompanion/backend'
assert (backend / 'app/security/scheduler_oidc.py').is_file()
dependencies = (backend / 'app/security/dependencies.py').read_text()
assert 'scheduler_oidc_verifier.verify' in dependencies
assert 'Invalid administrator identity' in dependencies
settings = (backend / 'app/config.py').read_text()
assert 'scheduler_service_account_email' in settings
requirements = (backend / 'requirements.txt').read_text()
assert 'google-auth' in requirements
assert (backend / 'tests/test_scheduler_oidc.py').is_file()

# Add the live-generation and scheduler contracts to the final application gate
# after the theme installer has already upgraded the cache/theme checks.
gate = root / '.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py'
gate_source = gate.read_text(encoding='utf-8')
needle = "  grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json\n"
extra = '''  local family_organizer=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
  local family_publication_test=MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyWorshipPublicationValidatorTest.kt
  local scheduler_oidc=MyStudyCompanion/backend/app/security/scheduler_oidc.py
  local scheduler_test=MyStudyCompanion/backend/tests/test_scheduler_oidc.py
  test -s "$family_publication_test"
  test -s "$scheduler_oidc"
  test -s "$scheduler_test"
  grep -Fq 'backendApi.generateFamilyWorship' "$family_organizer"
  grep -Fq 'contentSyncEngine.sync("family_worship_generated")' "$family_organizer"
  grep -Fq 'FamilyWorshipPublicationValidator.requirePublishable' "$family_organizer"
  ! grep -Fq 'Family Worship plan selected by the household organizer.' "$family_organizer"
  grep -Fq 'scheduler_oidc_verifier.verify' MyStudyCompanion/backend/app/security/dependencies.py
  grep -Fq 'scheduler_service_account_email' MyStudyCompanion/backend/app/config.py
'''
if extra not in gate_source:
    if needle not in gate_source:
        raise SystemExit('Could not locate the live-release final-gate insertion point.')
    gate_source = gate_source.replace(needle, needle + extra, 1)
gate_source = gate_source.replace(
    '    "ColorWheelDialog",\n',
    '    "ColorWheelDialog",\n    "backendApi.generateFamilyWorship",\n    "family_worship_generated",\n    "FamilyWorshipPublicationValidator",\n    "scheduler_oidc_verifier",\n    "scheduler_service_account_email",\n',
)
gate.write_text(gate_source, encoding='utf-8')

print('PASS: Family Worship uses authenticated backend generation, signed synchronization, exact-plan validation, and Cloud Scheduler OIDC support.')
PY

python3 -m compileall -q MyStudyCompanion/backend/app MyStudyCompanion/backend/tests

echo 'Applied My Study Companion 0.14.1 live connected release-completion layer.'
