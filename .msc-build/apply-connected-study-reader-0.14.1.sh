#!/usr/bin/env bash
set -euo pipefail

cat .msc-build/connected-study-reader-0.14.1.part*.b64 \
  | base64 --decode \
  > /tmp/msc-0141-connected-reader-overlay.tar.xz

echo '643a2d1d9f0a1e1b81d80e5448c0bbce77646f3e995a5b5b9c5d42975749094c  /tmp/msc-0141-connected-reader-overlay.tar.xz' \
  | sha256sum -c -

tar -xJf /tmp/msc-0141-connected-reader-overlay.tar.xz -C .

node --check MyStudyCompanionWeb/study-reader.js
node --check MyStudyCompanionWeb/study-library.js
node --check MyStudyCompanionWeb/firebase-sync.js
node --check MyStudyCompanionWeb/app.js

grep -Fq '0.14.1-private-alpha-connected-study-reader' MyStudyCompanion/app/build.gradle.kts
grep -Fq '0.14.1-wear-private-alpha-connected-study-reader' MyStudyCompanion/wear/build.gradle.kts
grep -Fq 'WindowInsets.safeDrawing' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt
grep -Fq 'Family Hub' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt
grep -Fq 'StudyReaderDialog' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/StudyReaderDialog.kt
grep -Fq 'data-view="readerView"' MyStudyCompanionWeb/index.html
grep -Fq 'msc-web-v0141-connected-study-reader' MyStudyCompanionWeb/sw.js
grep -Fq 'studyReader' MyStudyCompanionWeb/firebase-sync.js

echo 'Applied My Study Companion 0.14.1 connected Study Reader overlay.'
