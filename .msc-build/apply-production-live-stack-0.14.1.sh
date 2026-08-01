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

echo 'Applied persistent Firestore production storage, scheduled Family Worship generation, and stable /v1 backend routing.'
