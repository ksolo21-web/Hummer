#!/usr/bin/env bash
set -euo pipefail

base64 --decode .msc-build/production-live-stack-0.14.1.patch.xz.b64 \
  > /tmp/msc-production-live-stack-0.14.1.patch.xz
echo 'cc651da56d8611bf6ba4a406c1625dcec5804fbfd169178f138c9e2733401a50  /tmp/msc-production-live-stack-0.14.1.patch.xz' \
  | sha256sum -c -
xz -t /tmp/msc-production-live-stack-0.14.1.patch.xz
xz -dc /tmp/msc-production-live-stack-0.14.1.patch.xz \
  > /tmp/msc-production-live-stack-0.14.1.patch
patch --batch --forward -p1 < /tmp/msc-production-live-stack-0.14.1.patch

# Patch tools may leave backup or reject files after cleanly handled overlays.
# They are never part of the installable PWA and must not be packaged.
find MyStudyCompanionWeb -type f \( -name '*.orig' -o -name '*.rej' \) -delete
if [[ -n "$(find MyStudyCompanionWeb -type f \( -name '*.orig' -o -name '*.rej' \) -print -quit)" ]]; then
  echo 'Generated patch backup/reject files remain in the PWA tree.' >&2
  exit 1
fi

python3 -m compileall -q MyStudyCompanion/backend/app MyStudyCompanion/backend/tests
for file in MyStudyCompanionWeb/*.js; do
  node --check "$file"
done

grep -Fq 'persistence_backend: Literal["sqlite", "firestore"]' \
  MyStudyCompanion/backend/app/config.py
grep -Fq 'class FirestoreContentRepository' \
  MyStudyCompanion/backend/app/repositories/firestore_repositories.py
grep -Fq 'class FirestoreDeviceRepository' \
  MyStudyCompanion/backend/app/repositories/firestore_repositories.py
grep -Fq 'class FirestoreAiSessionRepository' \
  MyStudyCompanion/backend/app/repositories/firestore_repositories.py
grep -Fq '/admin/v1/automation/family-worship/scheduled' \
  MyStudyCompanion/backend/app/main.py
grep -Fq '"source": "/v1/**"' MyStudyCompanionWeb/firebase.json
grep -Fq 'google-cloud-firestore==2.28.0' \
  MyStudyCompanion/backend/requirements.txt
test -s MyStudyCompanion/backend/tests/test_firestore_repositories.py
test -s MyStudyCompanion/backend/tests/test_scheduled_family_worship_service.py

python3 - <<'PY'
from pathlib import Path

gate = Path('.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py')
source = gate.read_text(encoding='utf-8')
needle = "  grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json\n"
extra = '''  local firestore_repositories=MyStudyCompanion/backend/app/repositories/firestore_repositories.py
  local scheduled_family_service=MyStudyCompanion/backend/app/services/scheduled_family_worship_service.py
  test -s "$firestore_repositories"
  test -s "$scheduled_family_service"
  test -s MyStudyCompanion/backend/tests/test_firestore_repositories.py
  test -s MyStudyCompanion/backend/tests/test_scheduled_family_worship_service.py
  grep -Fq 'class FirestoreContentRepository' "$firestore_repositories"
  grep -Fq 'class FirestoreDeviceRepository' "$firestore_repositories"
  grep -Fq 'class FirestoreAiSessionRepository' "$firestore_repositories"
  grep -Fq 'persistence_backend: Literal["sqlite", "firestore"]' MyStudyCompanion/backend/app/config.py
  grep -Fq '/admin/v1/automation/family-worship/scheduled' MyStudyCompanion/backend/app/main.py
  grep -Fq '"source": "/v1/**"' MyStudyCompanionWeb/firebase.json
  grep -Fq 'MSC_BACKEND_BASE_URL' .github/workflows/msc-0.14.1-stable-private-alpha.yml
  grep -Fq 'MSC_PERSISTENCE_BACKEND=firestore' .github/workflows/msc-0.14.1-deploy-production-live-stack.yml
  grep -Fq 'msc-weekly-meeting-watchtower-refresh' .github/workflows/msc-0.14.1-deploy-production-live-stack.yml
  test -z "$(find MyStudyCompanionWeb -type f \\( -name '*.orig' -o -name '*.rej' \\) -print -quit)"
'''
if extra not in source:
    if needle not in source:
        raise SystemExit('Could not locate the production live-stack gate insertion point.')
    source = source.replace(needle, needle + extra, 1)
required_anchor = '    "scheduler_service_account_email",\n'
required_extra = (
    '    "FirestoreContentRepository",\n'
    '    "FirestoreDeviceRepository",\n'
    '    "FirestoreAiSessionRepository",\n'
    '    "persistence_backend",\n'
    '    "family-worship/scheduled",\n'
    '    "MSC_BACKEND_BASE_URL",\n'
    '    "MSC_PERSISTENCE_BACKEND=firestore",\n'
    '    "msc-weekly-meeting-watchtower-refresh",\n'
)
if '    "FirestoreContentRepository",\n' not in source:
    if required_anchor not in source:
        raise SystemExit('Could not locate the production required-marker insertion point.')
    source = source.replace(required_anchor, required_anchor + required_extra, 1)
gate.write_text(source, encoding='utf-8')
PY

echo 'Applied persistent Firestore production storage, scheduled Family Worship generation, clean PWA packaging, stable /v1 backend routing, and production release gates.'
