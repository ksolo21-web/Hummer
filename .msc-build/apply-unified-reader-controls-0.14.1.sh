#!/usr/bin/env bash
set -euo pipefail

cat .msc-build/unified-reader-controls-0.14.1.part*.b64 \
  | base64 --decode \
  > /tmp/msc-0141-unified-reader-controls.tar.xz

echo 'c4cf8237848ff1e838149144be8884abdd5f1fd12bf9adb675184879becb652e  /tmp/msc-0141-unified-reader-controls.tar.xz' \
  | sha256sum -c -
xz -t /tmp/msc-0141-unified-reader-controls.tar.xz
tar -xJf /tmp/msc-0141-unified-reader-controls.tar.xz -C .

python3 - <<'PY'
from pathlib import Path

path = Path('MyStudyCompanionWeb/app.js')
source = path.read_text(encoding='utf-8')
old = '''        highlights:[...new Set([...(localLibrary.highlights||[]),...(remoteLibrary.highlights||[])])],
        readingPosition:{...(localLibrary.readingPosition||{}),...(remoteLibrary.readingPosition||{})},
'''
new = '''        highlights:[...new Set([...(localLibrary.highlights||[]),...(remoteLibrary.highlights||[])])],
        bookmarks:[...new Set([...(localLibrary.bookmarks||[]),...(remoteLibrary.bookmarks||[])])],
        readingPosition:{...(localLibrary.readingPosition||{}),...(remoteLibrary.readingPosition||{})},
'''
if old in source:
    source = source.replace(old, new, 1)
elif new not in source:
    raise SystemExit('Web study-library merge anchor was not found.')
path.write_text(source, encoding='utf-8')
print('Preserved and unioned web Study Reader bookmarks during household pull-sync.')
PY

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
grep -Fq 'bookmarks:[...new Set([...(localLibrary.bookmarks||[]),...(remoteLibrary.bookmarks||[])])]' \
  MyStudyCompanionWeb/app.js
grep -Fq 'readerDocumentDictate' MyStudyCompanionWeb/index.html

echo 'Applied Unified Study Reader bookmarks, dictation, playback, and glasses controls.'
