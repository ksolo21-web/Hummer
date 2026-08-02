#!/usr/bin/env bash
set -euo pipefail

verify_sources() {
  grep -q 'versionCode = 34' MyStudyCompanion/app/build.gradle.kts
  grep -q '0.14.2-private-alpha-workbook-theme-repair' MyStudyCompanion/app/build.gradle.kts
  grep -q 'versionCode = 360142001' MyStudyCompanion/wear/build.gradle.kts
  grep -q '0.14.2-wear-private-alpha-workbook-theme-repair' MyStudyCompanion/wear/build.gradle.kts

  local model=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/InteractiveWorkbookModels.kt
  local editor=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
  local family=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt
  local family_hub=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyHubScreen.kt
  local app_shell=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt
  local cloud=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
  local reader_repo=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
  local reader_ui=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
  local wear=MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/MainActivity.kt

  # Preserve the complete 0.14.0 interactive workbook scope.
  grep -Fq 'WorkbookActivityKind.COLOR_BY_NUMBER' "$model"
  grep -Fq 'WorkbookActivityKind.MATCHING' "$model"
  grep -Fq 'WorkbookActivityKind.CROSSWORD' "$model"
  grep -Fq 'fun familyBook' "$model"
  grep -Fq 'PdfDocument' "$editor"
  grep -Fq 'Draw, color & handwrite' "$editor"
  grep -Fq 'Open interactive activity page' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/EventNotebooksSection.kt
  grep -Fq 'Interactive Family Worship' "$family"
  grep -Fq 'MEMBER_WORKBOOKS' "$cloud"
  grep -Fq 'MAX_WORKBOOK_PAGE_JSON = 700_000' "$cloud"
  grep -Fq 'memberWorkbooks' MyStudyCompanion/firestore.rules
  grep -Fq 'payloadJson.size() <= 700000' MyStudyCompanion/firestore.rules

  # Verify the 0.14.1 unified reader, notes, adaptive shell, and Family Hub.
  test -s "$reader_repo"
  test -s "$reader_ui"
  test -s "$family_hub"
  grep -Fq 'OfficialDailyTextRepository' "$reader_repo"
  grep -Fq 'OfficialWatchtowerStudyRepository' "$reader_repo"
  grep -Fq 'OfficialPageReader' "$reader_repo"
  grep -Fq 'memberStudyMaterials' "$reader_repo"
  grep -Fq 'TextToSpeech' "$reader_ui"
  grep -Fq 'Read Watchtower, listen & add paragraph notes' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/StudyScreen.kt
  grep -Fq 'safeDrawingPadding()' "$app_shell"
  grep -Fq 'AppRoute.FAMILY, "Family Hub"' "$app_shell"
  grep -Fq 'fun FamilyHubScreen(' "$family_hub"
  grep -Fq 'FamilyBoardSection' "$family_hub"
  grep -Fq 'HouseholdScreen' "$family_hub"
  grep -Fq 'memberStudyMaterials' MyStudyCompanion/firestore.rules
  grep -Fq 'payloadJson.size() <= 700000' MyStudyCompanion/firestore.rules

  # Preserve and extend Wear OS functionality.
  grep -Fq 'INTERACTIVE WORKBOOK' "$wear"
  grep -Fq 'Mark page complete' "$wear"
  grep -Fq 'Add voice note' "$wear"
  grep -Fq 'STUDY READER' "$wear"
  grep -Fq 'READER_POSITION_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'READER_NOTE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'WORKBOOK_COMPLETE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'WORKBOOK_NOTE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt

  # Preserve the web app and add the full glasses-friendly reader.
  test -s MyStudyCompanionWeb/workbook.js
  test -s MyStudyCompanionWeb/reader.js
  grep -Fq 'data-view="familyView"' MyStudyCompanionWeb/index.html
  grep -Fq 'id="studyLibraryList"' MyStudyCompanionWeb/index.html
  grep -Fq 'id="readerModal"' MyStudyCompanionWeb/index.html
  grep -Fq 'createWorkbookEngine' MyStudyCompanionWeb/workbook.js
  grep -Fq 'createStudyReader' MyStudyCompanionWeb/reader.js
  grep -Fq 'speechSynthesis' MyStudyCompanionWeb/reader.js
  grep -Fq 'notesByBlockId' MyStudyCompanionWeb/reader.js
  grep -Fq 'memberWorkbooks' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'memberStudyMaterials' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'msc-web-v0142-workbook-theme-release-v1' MyStudyCompanionWeb/sw.js
  for file in MyStudyCompanionWeb/*.js; do node --check "$file"; done

  # The exact previously approved content catalogs must survive the overlay.
  node <<'JS'
global.window = {};
require('./MyStudyCompanionWeb/pointers.js');
require('./MyStudyCompanionWeb/journeys.js');
require('./MyStudyCompanionWeb/event-programs.js');
if (window.MSC_POINTERS.length < 31) throw new Error('Daily pointers were lost.');
if (window.MSC_JOURNEYS.length < 30) throw new Error('Bible journeys were lost.');
if (window.MSC_EVENT_PROGRAMS.length !== 5) throw new Error('Official programs were lost.');
const parts = window.MSC_EVENT_PROGRAMS.reduce((sum, program) => sum + program.parts.length, 0);
if (parts !== 75) throw new Error(`Expected 75 program parts, found ${parts}.`);
console.log(`PASS: preserved ${window.MSC_POINTERS.length} pointers, ${window.MSC_JOURNEYS.length} journeys, and ${parts} program parts.`);
JS
}

install_firebase_config() {
  base64 --decode .msc-build/firebase-google-services-0.12.4.json.b64 > MyStudyCompanion/app/google-services.json
  echo '5b2f85f67e6cb33e1fbe0a58b704a5c971bf2faf6f1b6e09730e904d02b91b5e  MyStudyCompanion/app/google-services.json' | sha256sum -c -
  MSC_GOOGLE_WEB_CLIENT_ID="$(python3 - <<'PY'
import json
from pathlib import Path
data = json.loads(Path('MyStudyCompanion/app/google-services.json').read_text())
assert data['project_info']['project_id'] == 'my-study-companion-abc01'
clients = {item['client_info']['android_client_info']['package_name']: item for item in data['client']}
canonical = clients['com.mystudycompanion.app']
android_clients = [item for item in canonical['oauth_client'] if item['client_type'] == 1]
web_clients = [item for item in canonical['oauth_client'] if item['client_type'] == 3]
assert len(android_clients) == 1
assert android_clients[0]['android_info']['certificate_hash'] == '1997d421d177215a44f9651ce53dbaec152fbc49'
assert len(web_clients) == 1
print(web_clients[0]['client_id'])
PY
)"
  export MSC_GOOGLE_WEB_CLIENT_ID
  echo 'PASS: canonical Firebase package and OAuth binding validated.'
}

create_ci_signing_key() {
  local signing_dir="${RUNNER_TEMP:-/tmp}/msc-temporary-signing"
  mkdir -p "$signing_dir"
  keytool -genkeypair -noprompt \
    -keystore "$signing_dir/temporary.jks" \
    -storepass temporary-build-only \
    -alias temporary-build-only \
    -keypass temporary-build-only \
    -dname 'CN=Temporary CI Build,O=My Study Companion,C=US' \
    -keyalg RSA -keysize 2048 -validity 30
  export MSC_RELEASE_STORE_FILE="$signing_dir/temporary.jks"
  export MSC_RELEASE_STORE_PASSWORD=temporary-build-only
  export MSC_RELEASE_KEY_ALIAS=temporary-build-only
  export MSC_RELEASE_KEY_PASSWORD=temporary-build-only
}

package_release() {
  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  local aapt="$sdk_root/build-tools/36.0.0/aapt"
  local apksigner="$sdk_root/build-tools/36.0.0/apksigner"
  local zipalign="$sdk_root/build-tools/36.0.0/zipalign"
  mkdir -p final-dist/test-reports

  local phone_private wear_private phone_debug wear_debug
  phone_private="$(find MyStudyCompanion/app/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
  wear_private="$(find MyStudyCompanion/wear/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
  phone_debug="$(find MyStudyCompanion/app/build/outputs/apk/debug -name '*.apk' -type f -print -quit)"
  wear_debug="$(find MyStudyCompanion/wear/build/outputs/apk/debug -name '*.apk' -type f -print -quit)"
  test -f "$phone_private"; test -f "$wear_private"; test -f "$phone_debug"; test -f "$wear_debug"

  cp "$phone_private" final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk
  cp "$wear_private" final-dist/MyStudyCompanion-wear-0.14.2-canonical-temporary-signed.apk
  cp "$phone_debug" final-dist/MyStudyCompanion-phone-0.14.2-debug.apk
  cp "$wear_debug" final-dist/MyStudyCompanion-wear-0.14.2-debug.apk
  unzip -tq final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk
  unzip -tq final-dist/MyStudyCompanion-wear-0.14.2-canonical-temporary-signed.apk

  "$aapt" dump badging final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk | tee final-dist/PHONE-CANONICAL-IDENTITY.txt
  grep -q "package: name='com.mystudycompanion.app' versionCode='34'" final-dist/PHONE-CANONICAL-IDENTITY.txt
  grep -q "versionName='0.14.2-private-alpha-workbook-theme-repair'" final-dist/PHONE-CANONICAL-IDENTITY.txt
  "$aapt" dump badging final-dist/MyStudyCompanion-wear-0.14.2-canonical-temporary-signed.apk | tee final-dist/WEAR-CANONICAL-IDENTITY.txt
  grep -q "package: name='com.mystudycompanion.app' versionCode='360142001'" final-dist/WEAR-CANONICAL-IDENTITY.txt
  grep -q "versionName='0.14.2-wear-private-alpha-workbook-theme-repair'" final-dist/WEAR-CANONICAL-IDENTITY.txt

  "$aapt" dump resources final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk > final-dist/FIREBASE-RESOURCE-AUDIT.txt
  grep -q 'google_app_id' final-dist/FIREBASE-RESOURCE-AUDIT.txt
  grep -q 'default_web_client_id' final-dist/FIREBASE-RESOURCE-AUDIT.txt
  grep -q 'project_id' final-dist/FIREBASE-RESOURCE-AUDIT.txt
  "$apksigner" verify --verbose --print-certs final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk > final-dist/PHONE-TEMPORARY-SIGNATURE.txt
  "$apksigner" verify --verbose --print-certs final-dist/MyStudyCompanion-wear-0.14.2-canonical-temporary-signed.apk > final-dist/WEAR-TEMPORARY-SIGNATURE.txt
  "$zipalign" -c -P 16 -v 4 final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk > final-dist/PHONE-ZIPALIGN.txt
  "$zipalign" -c -P 16 -v 4 final-dist/MyStudyCompanion-wear-0.14.2-canonical-temporary-signed.apk > final-dist/WEAR-ZIPALIGN.txt

  rm -rf /tmp/phone-strings /tmp/wear-strings
  mkdir -p /tmp/phone-strings /tmp/wear-strings
  unzip -q final-dist/MyStudyCompanion-phone-0.14.2-debug.apk 'classes*.dex' -d /tmp/phone-strings
  unzip -q final-dist/MyStudyCompanion-wear-0.14.2-debug.apk 'classes*.dex' -d /tmp/wear-strings
  find /tmp/phone-strings -name 'classes*.dex' -type f -print0 | xargs -0 strings -a -n 4 > final-dist/PHONE-STRING-AUDIT.txt
  find /tmp/wear-strings -name 'classes*.dex' -type f -print0 | xargs -0 strings -a -n 4 > final-dist/WEAR-STRING-AUDIT.txt
  grep -Fq 'Draw, color & handwrite' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Interactive Family Worship' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Open interactive activity page' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Study Reader' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Family Hub' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Read Watchtower, listen & add paragraph notes' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'INTERACTIVE WORKBOOK' final-dist/WEAR-STRING-AUDIT.txt
  grep -Fq 'Add voice note' final-dist/WEAR-STRING-AUDIT.txt
  grep -Fq 'STUDY READER' final-dist/WEAR-STRING-AUDIT.txt

  cp -R MyStudyCompanion/app/build/reports/tests/testDebugUnitTest final-dist/test-reports/phone
  cp -R MyStudyCompanion/wear/build/reports/tests/testDebugUnitTest final-dist/test-reports/wear
  cp MyStudyCompanion/firestore.rules final-dist/firestore.rules
  cp .msc-build/firebase-rules-tests/rules.test.cjs final-dist/firestore-rules.test.cjs
  (cd MyStudyCompanionWeb && zip -qr ../final-dist/MyStudyCompanion-Web-0.14.2-PWA.zip .)
  sha256sum final-dist/MyStudyCompanion-phone-0.14.2-canonical-temporary-signed.apk \
    final-dist/MyStudyCompanion-wear-0.14.2-canonical-temporary-signed.apk \
    final-dist/MyStudyCompanion-Web-0.14.2-PWA.zip final-dist/firestore.rules > final-dist/SHA256SUMS.txt

  cat > final-dist/VERIFICATION-REPORT.txt <<'EOF'
My Study Companion 0.14.2 Workbook Theme Repair — Final Release Candidate
PASS: The complete 0.14.0 interactive Assembly, Convention, and Family Worship workbook engine remains present, including drawing, coloring, matching, crosswords, notes, offline storage, and PDF export.
PASS: Android phone/tablet includes a unified reader for Daily Text, scriptures, Watchtower, meeting material, journeys, Family Worship, events, and verified official JW/WOL pages.
PASS: Study material uses stable block IDs for paragraph notes, highlights, reading position, and cross-device synchronization.
PASS: The Android reader includes TextToSpeech playback and Watchtower paragraph-note entry.
PASS: The app shell applies safe drawing insets globally, and family/household tools are consolidated under Family Hub.
PASS: Wear OS receives active reader position and supports previous/next paragraph, paragraph voice note, phone handoff, workbook progress, and existing companion functions.
PASS: The PWA includes a large-text glasses-friendly Study Library, browser speech controls, paragraph/document notes, highlights, offline storage, workbook parity, and Firebase synchronization.
PASS: Firestore isolates personal study materials to the owning account, validates official source URLs and strict fields, and enforces the 700 KB document limit.
PASS: Existing Daily Text, meeting content, Watchtower, Bible journeys, event programs, themes, authentication, family features, exact links, and app upgrade identity remain in the reconstructed build.
PASS: Phone and Wear unit tests, compilation, debug builds, canonical private-alpha builds, web syntax checks, APK identity/resource audits, and package creation completed.
NOTE: Permanent release signing, physical Z Fold/Flip/tablet/watch interaction, and real Meta-glasses browser behavior remain final real-device gates after CI.
EOF
}

run_build() {
  verify_sources
  install_firebase_config
  create_ci_signing_key
  (cd MyStudyCompanion && gradle --no-daemon --stacktrace \
    -PMSC_LOCAL_OWNER_MODE=true \
    -PMSC_GOOGLE_WEB_CLIENT_ID="$MSC_GOOGLE_WEB_CLIENT_ID" \
    :app:testDebugUnitTest :wear:testDebugUnitTest \
    :app:compileDebugKotlin :wear:compileDebugKotlin \
    :app:assembleDebug :wear:assembleDebug \
    :app:assemblePrivateAlpha :wear:assemblePrivateAlpha)
  package_release
}

run_firestore() {
  cp MyStudyCompanion/firestore.rules .msc-build/firebase-rules-tests/firestore.rules
  cd .msc-build/firebase-rules-tests
  npm ci
  npx --yes firebase-tools@15.1.0 emulators:exec \
    --only firestore --project demo-my-study-companion \
    "node rules.test.cjs" | tee FIRESTORE-RULES-TEST-RESULTS.txt
  grep -Fq 'PASS: 26 Firestore authorization, integrity, and abuse tests completed.' FIRESTORE-RULES-TEST-RESULTS.txt
}

case "${1:-}" in
  build) run_build ;;
  firestore) run_firestore ;;
  *) echo 'usage: run-interactive-workbooks-0.14.0-ci.sh build|firestore' >&2; exit 2 ;;
esac
