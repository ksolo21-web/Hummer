#!/usr/bin/env bash
set -euo pipefail

cat .msc-build/unified-reader-controls-0.14.1.part*.b64 \
  | base64 --decode \
  > /tmp/msc-0141-unified-reader-controls.tar.xz

echo 'c4cf8237848ff1e838149144be8884abdd5f1fd12bf9adb675184879becb652e  /tmp/msc-0141-unified-reader-controls.tar.xz' \
  | sha256sum -c -
xz -t /tmp/msc-0141-unified-reader-controls.tar.xz
tar -xJf /tmp/msc-0141-unified-reader-controls.tar.xz -C .

for file in MyStudyCompanionWeb/*.js; do
  node --check "$file"
done

grep -Fq 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
grep -Fq 'speechRate' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
grep -Fq 'bookmarks' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModels.kt
grep -Fq 'toggleBookmark' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
test -s \
  MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModelsTest.kt
grep -Fq 'SpeechRecognition' MyStudyCompanionWeb/reader.js
grep -Fq 'readerGlasses' MyStudyCompanionWeb/reader.js
grep -Fq 'bookmarks' MyStudyCompanionWeb/firebase-sync.js
grep -Fq 'readerDocumentDictate' MyStudyCompanionWeb/index.html

echo 'Applied Unified Study Reader bookmarks, dictation, playback, and glasses controls.'
