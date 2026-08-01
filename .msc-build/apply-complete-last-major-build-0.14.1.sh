#!/usr/bin/env bash
set -euo pipefail

cat .msc-build/complete-last-major-build-0.14.1.part*.b64 \
  | tr -d '\n' \
  | base64 --decode \
  > /tmp/msc-0141-complete-last-major.patch.xz

echo '5f92d0a0134d7c97e75df02da388671edd121c36a455cde3b28e496dfa8ac143  /tmp/msc-0141-complete-last-major.patch.xz' \
  | sha256sum -c -
xz -t /tmp/msc-0141-complete-last-major.patch.xz
xz -dc /tmp/msc-0141-complete-last-major.patch.xz > /tmp/msc-0141-complete-last-major.patch
patch --batch --forward -p1 < /tmp/msc-0141-complete-last-major.patch

for file in MyStudyCompanionWeb/*.js; do
  node --check "$file"
done
node --test MyStudyCompanionWeb/study-library-merge.test.mjs

grep -Fq 'FamilyWorshipReminderScheduler.schedule' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/CompanionHubRepository.kt
grep -Fq 'class FamilyWorshipReminderWorker' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipReminderScheduler.kt
grep -Fq 'mergeStudyReaderPackets' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModels.kt
grep -Fq 'revisionByDocument' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
grep -Fq 'Icons.Outlined.Repeat' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
grep -Fq 'Read my note' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
grep -Fq 'official/read' MyStudyCompanion/backend/app/main.py
grep -Fq 'class OfficialReaderService' \
  MyStudyCompanion/backend/app/services/official_reader_service.py
grep -Fq 'mergeStudyLibraries' MyStudyCompanionWeb/study-library-merge.js
grep -Fq 'createWearableBridge' MyStudyCompanionWeb/wearable-bridge.js
grep -Fq 'runtime-config.json' MyStudyCompanionWeb/firebase-config.js
grep -Fq 'readerRepeat' MyStudyCompanionWeb/index.html
grep -Fq 'msc-web-v0142-complete-reader' MyStudyCompanionWeb/sw.js
grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json

echo 'Applied the complete My Study Companion 0.14.1 last-major-build acceptance layer.'
