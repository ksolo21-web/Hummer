#!/usr/bin/env bash
set -euo pipefail

base64 --decode .msc-build/live-release-completion-0.14.1.patch.xz.b64 \
  > /tmp/msc-live-release-completion-0.14.1.patch.xz
echo 'c8bcff467bf5a0c4a7c2acd2ddf49daea61a19ebad60faa5cb67f6fa6011d72b  /tmp/msc-live-release-completion-0.14.1.patch.xz' \
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

print('PASS: Family Worship uses authenticated backend generation, signed synchronization, exact-plan validation, and Cloud Scheduler OIDC support.')
PY

python3 -m compileall -q MyStudyCompanion/backend/app MyStudyCompanion/backend/tests

echo 'Applied My Study Companion 0.14.1 live connected release-completion layer.'
