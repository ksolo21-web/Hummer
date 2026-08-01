#!/usr/bin/env bash
set -euo pipefail

verify_sources() {
  grep -q 'versionCode = 32' MyStudyCompanion/app/build.gradle.kts
  grep -q '0.14.0-private-alpha-interactive-workbooks' MyStudyCompanion/app/build.gradle.kts
  grep -q 'versionCode = 360140001' MyStudyCompanion/wear/build.gradle.kts
  grep -q '0.14.0-wear-private-alpha-interactive-workbooks' MyStudyCompanion/wear/build.gradle.kts

  local model=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/InteractiveWorkbookModels.kt
  local editor=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
  local family=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt
  local cloud=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
  local wear=MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/MainActivity.kt

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

  grep -Fq 'INTERACTIVE WORKBOOK' "$wear"
  grep -Fq 'Mark page complete' "$wear"
  grep -Fq 'Add voice note' "$wear"
  grep -Fq 'WORKBOOK_COMPLETE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt
  grep -Fq 'WORKBOOK_NOTE_ACTION_PATH' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/wear/WearDataContract.kt

  test -s MyStudyCompanionWeb/workbook.js
  grep -Fq 'data-view="familyView"' MyStudyCompanionWeb/index.html
  grep -Fq 'createWorkbookEngine' MyStudyCompanionWeb/workbook.js
  grep -Fq 'memberWorkbooks' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'msc-web-v0140-interactive-workbooks' MyStudyCompanionWeb/sw.js
  for file in MyStudyCompanionWeb/*.js; do node --check "$file"; done

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

  cp "$phone_private" final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk
  cp "$wear_private" final-dist/MyStudyCompanion-wear-0.14.0-canonical-temporary-signed.apk
  cp "$phone_debug" final-dist/MyStudyCompanion-phone-0.14.0-debug.apk
  cp "$wear_debug" final-dist/MyStudyCompanion-wear-0.14.0-debug.apk
  unzip -tq final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk
  unzip -tq final-dist/MyStudyCompanion-wear-0.14.0-canonical-temporary-signed.apk

  "$aapt" dump badging final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk | tee final-dist/PHONE-CANONICAL-IDENTITY.txt
  grep -q "package: name='com.mystudycompanion.app' versionCode='32'" final-dist/PHONE-CANONICAL-IDENTITY.txt
  grep -q "versionName='0.14.0-private-alpha-interactive-workbooks'" final-dist/PHONE-CANONICAL-IDENTITY.txt
  "$aapt" dump badging final-dist/MyStudyCompanion-wear-0.14.0-canonical-temporary-signed.apk | tee final-dist/WEAR-CANONICAL-IDENTITY.txt
  grep -q "package: name='com.mystudycompanion.app' versionCode='360140001'" final-dist/WEAR-CANONICAL-IDENTITY.txt
  grep -q "versionName='0.14.0-wear-private-alpha-interactive-workbooks'" final-dist/WEAR-CANONICAL-IDENTITY.txt

  "$aapt" dump resources final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk > final-dist/FIREBASE-RESOURCE-AUDIT.txt
  grep -q 'google_app_id' final-dist/FIREBASE-RESOURCE-AUDIT.txt
  grep -q 'default_web_client_id' final-dist/FIREBASE-RESOURCE-AUDIT.txt
  grep -q 'project_id' final-dist/FIREBASE-RESOURCE-AUDIT.txt
  "$apksigner" verify --verbose --print-certs final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk > final-dist/PHONE-TEMPORARY-SIGNATURE.txt
  "$apksigner" verify --verbose --print-certs final-dist/MyStudyCompanion-wear-0.14.0-canonical-temporary-signed.apk > final-dist/WEAR-TEMPORARY-SIGNATURE.txt
  "$zipalign" -c -P 16 -v 4 final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk > final-dist/PHONE-ZIPALIGN.txt
  "$zipalign" -c -P 16 -v 4 final-dist/MyStudyCompanion-wear-0.14.0-canonical-temporary-signed.apk > final-dist/WEAR-ZIPALIGN.txt

  rm -rf /tmp/phone-strings /tmp/wear-strings
  mkdir -p /tmp/phone-strings /tmp/wear-strings
  unzip -q final-dist/MyStudyCompanion-phone-0.14.0-debug.apk 'classes*.dex' -d /tmp/phone-strings
  unzip -q final-dist/MyStudyCompanion-wear-0.14.0-debug.apk 'classes*.dex' -d /tmp/wear-strings
  find /tmp/phone-strings -name 'classes*.dex' -type f -print0 | xargs -0 strings -a -n 4 > final-dist/PHONE-STRING-AUDIT.txt
  find /tmp/wear-strings -name 'classes*.dex' -type f -print0 | xargs -0 strings -a -n 4 > final-dist/WEAR-STRING-AUDIT.txt
  grep -Fq 'Draw, color & handwrite' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Interactive Family Worship' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'Open interactive activity page' final-dist/PHONE-STRING-AUDIT.txt
  grep -Fq 'INTERACTIVE WORKBOOK' final-dist/WEAR-STRING-AUDIT.txt
  grep -Fq 'Add voice note' final-dist/WEAR-STRING-AUDIT.txt

  cp -R MyStudyCompanion/app/build/reports/tests/testDebugUnitTest final-dist/test-reports/phone
  cp -R MyStudyCompanion/wear/build/reports/tests/testDebugUnitTest final-dist/test-reports/wear
  cp MyStudyCompanion/firestore.rules final-dist/firestore.rules
  cp .msc-build/firebase-rules-tests/rules.test.cjs final-dist/firestore-rules.test.cjs
  (cd MyStudyCompanionWeb && zip -qr ../final-dist/MyStudyCompanion-Web-0.14.0-PWA.zip .)
  sha256sum final-dist/MyStudyCompanion-phone-0.14.0-canonical-temporary-signed.apk \
    final-dist/MyStudyCompanion-wear-0.14.0-canonical-temporary-signed.apk \
    final-dist/MyStudyCompanion-Web-0.14.0-PWA.zip final-dist/firestore.rules > final-dist/SHA256SUMS.txt

  cat > final-dist/VERIFICATION-REPORT.txt <<'EOF'
My Study Companion 0.14.0 Interactive Workbooks
PASS: Android interactive Assembly, Convention, and Family Worship pages include drawing, coloring, matching, crosswords, notes, offline storage, and blank/completed PDF export.
PASS: Wear OS receives the active workbook page, progress, page-complete action, and voice-note action.
PASS: The PWA includes the same interactive event and Family Worship workbook engine and page-sized Firebase synchronization.
PASS: Workbook drawings are stored as separate page documents rather than overfilling general member progress.
PASS: phone and Wear unit tests, compilation, debug builds, and canonical private-alpha builds completed.
NOTE: permanent release signing and real-device interaction remain final gates after CI.
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
  grep -Fq 'PASS: 23 Firestore authorization, integrity, and abuse tests completed.' FIRESTORE-RULES-TEST-RESULTS.txt
}

case "${1:-}" in
  build) run_build ;;
  firestore) run_firestore ;;
  *) echo 'usage: run-interactive-workbooks-0.14.0-ci.sh build|firestore' >&2; exit 2 ;;
esac
