#!/usr/bin/env python3
from pathlib import Path

path = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = path.read_text(encoding='utf-8')

unified_web_gate = """grep -Fq 'msc-web-v0142-complete-reader' MyStudyCompanionWeb/sw.js

  local reader_models=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModels.kt
  local reader_repo=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
  local reader_ui=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
  local reader_test=MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderModelsTest.kt
  local family_hub=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyHubScreen.kt
  local family_reminder=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipReminderScheduler.kt
  local family_reminder_test=MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyWorshipReminderSchedulerTest.kt
  local official_reader=MyStudyCompanion/backend/app/services/official_reader_service.py
  local app_shell=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt
  test -s \"$reader_models\"
  test -s \"$reader_repo\"
  test -s \"$reader_ui\"
  test -s \"$reader_test\"
  test -s \"$family_hub\"
  test -s \"$family_reminder\"
  test -s \"$family_reminder_test\"
  test -s \"$official_reader\"
  grep -Fq 'OfficialDailyTextRepository' \"$reader_repo\"
  grep -Fq 'OfficialWatchtowerStudyRepository' \"$reader_repo\"
  grep -Fq 'OfficialPageReader' \"$reader_repo\"
  grep -Fq 'memberStudyMaterials' \"$reader_repo\"
  grep -Fq 'toggleBookmark' \"$reader_repo\"
  grep -Fq 'revisionByDocument' \"$reader_repo\"
  grep -Fq 'mergeStudyReaderPackets' \"$reader_models\"
  grep -Fq 'bookmarks' \"$reader_models\"
  grep -Fq 'TextToSpeech' \"$reader_ui\"
  grep -Fq 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' \"$reader_ui\"
  grep -Fq 'speechRate' \"$reader_ui\"
  grep -Fq 'Icons.Outlined.Repeat' \"$reader_ui\"
  grep -Fq 'Read my note' \"$reader_ui\"
  grep -Fq 'FamilyWorshipReminderWorker' \"$family_reminder\"
  grep -Fq 'safeDrawingPadding()' \"$app_shell\"
  grep -Fq 'AppRoute.FAMILY, \"Family Hub\"' \"$app_shell\"
  grep -Fq 'fun FamilyHubScreen(' \"$family_hub\"
  grep -Fq 'FamilyBoardSection' \"$family_hub\"
  grep -Fq 'HouseholdScreen' \"$family_hub\"
  grep -Fq 'memberStudyMaterials' MyStudyCompanion/firestore.rules
  grep -Fq 'STUDY READER' \"$wear\"
  grep -Fq 'READER_POSITION_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'READER_NOTE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'official/read' MyStudyCompanion/backend/app/main.py
  grep -Fq 'class OfficialReaderService' \"$official_reader\"
  test -s MyStudyCompanionWeb/reader.js
  test -s MyStudyCompanionWeb/study-library-merge.js
  test -s MyStudyCompanionWeb/study-library-merge.test.mjs
  test -s MyStudyCompanionWeb/wearable-bridge.js
  grep -Fq 'id=\"studyLibraryList\"' MyStudyCompanionWeb/index.html
  grep -Fq 'id=\"readerModal\"' MyStudyCompanionWeb/index.html
  grep -Fq 'readerDocumentDictate' MyStudyCompanionWeb/index.html
  grep -Fq 'readerRepeat' MyStudyCompanionWeb/index.html
  grep -Fq 'createStudyReader' MyStudyCompanionWeb/reader.js
  grep -Fq 'speechSynthesis' MyStudyCompanionWeb/reader.js
  grep -Fq 'SpeechRecognition' MyStudyCompanionWeb/reader.js
  grep -Fq 'readerGlasses' MyStudyCompanionWeb/reader.js
  grep -Fq 'readNote' MyStudyCompanionWeb/reader.js
  grep -Fq 'notesByBlockId' MyStudyCompanionWeb/reader.js
  grep -Fq 'bookmarks' MyStudyCompanionWeb/reader.js
  grep -Fq 'memberStudyMaterials' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'revision' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'mergeStudyLibraries' MyStudyCompanionWeb/study-library-merge.js
  grep -Fq 'createWearableBridge' MyStudyCompanionWeb/wearable-bridge.js
  grep -Fq 'runtime-config.json' MyStudyCompanionWeb/firebase-config.js
  grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json
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
    "My Study Companion 0.14.0 Interactive Workbooks": "My Study Companion 0.14.1 Complete Study Reader",
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
    "msc-web-v0142-complete-reader",
    "OfficialWatchtowerStudyRepository",
    "mergeStudyReaderPackets",
    "revisionByDocument",
    "FamilyWorshipReminderWorker",
    "Icons.Outlined.Repeat",
    "Read my note",
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
    "readerRepeat",
    "createStudyReader",
    "memberStudyMaterials",
    "mergeStudyLibraries",
    "createWearableBridge",
    "runtime-config.json",
    "OfficialReaderService",
    "my-study-companion-private",
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
    "msc-web-v0141-unified-study-reader",
)
for marker in stale:
    if marker in source:
        raise SystemExit(f'Stale CI marker remains: {marker}')

path.write_text(source, encoding='utf-8')
print(
    f'Updated {changed} final identity, artifact, complete-reader, conflict, '
    'reminder, backend, wearable, signing, and deployment gate occurrence(s).'
)
