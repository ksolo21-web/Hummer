#!/usr/bin/env python3
from pathlib import Path

path = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = path.read_text(encoding='utf-8')

unified_web_gate = """grep -Fq 'msc-web-v0141-unified-study-reader' MyStudyCompanionWeb/sw.js

  local reader_models=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModels.kt
  local reader_repo=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
  local reader_ui=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
  local reader_test=MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModelsTest.kt
  local family_hub=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyHubScreen.kt
  local app_shell=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt
  test -s \"$reader_models\"
  test -s \"$reader_repo\"
  test -s \"$reader_ui\"
  test -s \"$reader_test\"
  test -s \"$family_hub\"
  grep -Fq 'OfficialDailyTextRepository' \"$reader_repo\"
  grep -Fq 'OfficialWatchtowerStudyRepository' \"$reader_repo\"
  grep -Fq 'OfficialPageReader' \"$reader_repo\"
  grep -Fq 'memberStudyMaterials' \"$reader_repo\"
  grep -Fq 'toggleBookmark' \"$reader_repo\"
  grep -Fq 'bookmarks' \"$reader_models\"
  grep -Fq 'TextToSpeech' \"$reader_ui\"
  grep -Fq 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' \"$reader_ui\"
  grep -Fq 'speechRate' \"$reader_ui\"
  grep -Fq 'Pause' \"$reader_ui\"
  grep -Fq 'safeDrawingPadding()' \"$app_shell\"
  grep -Fq 'AppRoute.FAMILY, \"Family Hub\"' \"$app_shell\"
  grep -Fq 'fun FamilyHubScreen(' \"$family_hub\"
  grep -Fq 'FamilyBoardSection' \"$family_hub\"
  grep -Fq 'HouseholdScreen' \"$family_hub\"
  grep -Fq 'memberStudyMaterials' MyStudyCompanion/firestore.rules
  grep -Fq 'STUDY READER' \"$wear\"
  grep -Fq 'READER_POSITION_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'READER_NOTE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  test -s MyStudyCompanionWeb/reader.js
  grep -Fq 'id=\"studyLibraryList\"' MyStudyCompanionWeb/index.html
  grep -Fq 'id=\"readerModal\"' MyStudyCompanionWeb/index.html
  grep -Fq 'readerDocumentDictate' MyStudyCompanionWeb/index.html
  grep -Fq 'createStudyReader' MyStudyCompanionWeb/reader.js
  grep -Fq 'speechSynthesis' MyStudyCompanionWeb/reader.js
  grep -Fq 'SpeechRecognition' MyStudyCompanionWeb/reader.js
  grep -Fq 'readerGlasses' MyStudyCompanionWeb/reader.js
  grep -Fq 'notesByBlockId' MyStudyCompanionWeb/reader.js
  grep -Fq 'bookmarks' MyStudyCompanionWeb/reader.js
  grep -Fq 'memberStudyMaterials' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'bookmarks' MyStudyCompanionWeb/firebase-sync.js
"""

replacements = {
    "grep -q 'versionCode = 32'": "grep -q 'versionCode = 33'",
    "grep -q '0.14.0-private-alpha-interactive-workbooks'": "grep -q '0.14.1-private-alpha-unified-study-reader'",
    "grep -q 'versionCode = 360140001'": "grep -q 'versionCode = 360141001'",
    "grep -q '0.14.0-wear-private-alpha-interactive-workbooks'": "grep -q '0.14.1-wear-private-alpha-unified-study-reader'",
    "versionCode='32'": "versionCode='33'",
    "versionName='0.14.0-private-alpha-interactive-workbooks'": "versionName='0.14.1-private-alpha-unified-study-reader'",
    "versionCode='360140001'": "versionCode='360141001'",
    "versionName='0.14.0-wear-private-alpha-interactive-workbooks'": "versionName='0.14.1-wear-private-alpha-unified-study-reader'",
    "MyStudyCompanion-phone-0.14.0": "MyStudyCompanion-phone-0.14.1",
    "MyStudyCompanion-wear-0.14.0": "MyStudyCompanion-wear-0.14.1",
    "MyStudyCompanion-Web-0.14.0-PWA.zip": "MyStudyCompanion-Web-0.14.1-PWA.zip",
    "My Study Companion 0.14.0 Interactive Workbooks": "My Study Companion 0.14.1 Unified Study Reader",
    "grep -Fq 'msc-web-v0140-interactive-workbooks' MyStudyCompanionWeb/sw.js\n": unified_web_gate,
}

changed = 0
for old, new in replacements.items():
    count = source.count(old)
    if count:
        source = source.replace(old, new)
        changed += count

required = (
    "grep -q 'versionCode = 33'",
    "0.14.1-private-alpha-unified-study-reader",
    "grep -q 'versionCode = 360141001'",
    "0.14.1-wear-private-alpha-unified-study-reader",
    "MyStudyCompanion-phone-0.14.1-canonical-temporary-signed.apk",
    "MyStudyCompanion-wear-0.14.1-canonical-temporary-signed.apk",
    "MyStudyCompanion-Web-0.14.1-PWA.zip",
    "msc-web-v0141-unified-study-reader",
    "OfficialWatchtowerStudyRepository",
    "safeDrawingPadding()",
    "Family Hub",
    "STUDY READER",
    "RecognizerIntent.ACTION_RECOGNIZE_SPEECH",
    "speechRate",
    "toggleBookmark",
    "UnifiedStudyReaderModelsTest.kt",
    "SpeechRecognition",
    "readerGlasses",
    "readerDocumentDictate",
    "createStudyReader",
    "memberStudyMaterials",
    "bookmarks",
)
for marker in required:
    if marker not in source:
        raise SystemExit(f'Missing corrected 0.14.1 CI marker: {marker}')

stale = (
    "grep -q 'versionCode = 32'",
    "versionCode='32'",
    "0.14.0-private-alpha-interactive-workbooks",
    "versionCode='360140001'",
    "0.14.0-wear-private-alpha-interactive-workbooks",
    "MyStudyCompanion-phone-0.14.0",
    "MyStudyCompanion-wear-0.14.0",
    "MyStudyCompanion-Web-0.14.0-PWA.zip",
    "msc-web-v0140-interactive-workbooks",
)
for marker in stale:
    if marker in source:
        raise SystemExit(f'Stale 0.14.0 CI marker remains: {marker}')

path.write_text(source, encoding='utf-8')
print(
    f'Updated {changed} final identity, artifact, cache, unified-reader, '
    'bookmark, dictation, playback, and glasses gate occurrence(s) for 0.14.1.'
)
