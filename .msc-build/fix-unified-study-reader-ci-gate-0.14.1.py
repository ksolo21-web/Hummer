#!/usr/bin/env python3
from pathlib import Path

path = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = path.read_text(encoding='utf-8')

# Normalize every historical runner identity to the final 0.14.2 release. The
# reconstruction overlays can restore an older copy of this runner, so this
# repair must be idempotent and must not depend on one exact historical anchor.
replacements = {
    "grep -q 'versionCode = 32'": "grep -q 'versionCode = 34'",
    "grep -q 'versionCode = 33'": "grep -q 'versionCode = 34'",
    "grep -q '0.14.0-private-alpha-interactive-workbooks'": "grep -q '0.14.2-private-alpha-workbook-theme-repair'",
    "grep -q '0.14.1-private-alpha-unified-study-reader'": "grep -q '0.14.2-private-alpha-workbook-theme-repair'",
    "grep -q 'versionCode = 360140001'": "grep -q 'versionCode = 360142001'",
    "grep -q 'versionCode = 360141001'": "grep -q 'versionCode = 360142001'",
    "grep -q '0.14.0-wear-private-alpha-interactive-workbooks'": "grep -q '0.14.2-wear-private-alpha-workbook-theme-repair'",
    "grep -q '0.14.1-wear-private-alpha-unified-study-reader'": "grep -q '0.14.2-wear-private-alpha-workbook-theme-repair'",
    "versionCode='32'": "versionCode='34'",
    "versionCode='33'": "versionCode='34'",
    "versionName='0.14.0-private-alpha-interactive-workbooks'": "versionName='0.14.2-private-alpha-workbook-theme-repair'",
    "versionName='0.14.1-private-alpha-unified-study-reader'": "versionName='0.14.2-private-alpha-workbook-theme-repair'",
    "versionCode='360140001'": "versionCode='360142001'",
    "versionCode='360141001'": "versionCode='360142001'",
    "versionName='0.14.0-wear-private-alpha-interactive-workbooks'": "versionName='0.14.2-wear-private-alpha-workbook-theme-repair'",
    "versionName='0.14.1-wear-private-alpha-unified-study-reader'": "versionName='0.14.2-wear-private-alpha-workbook-theme-repair'",
    "MyStudyCompanion-phone-0.14.0": "MyStudyCompanion-phone-0.14.2",
    "MyStudyCompanion-phone-0.14.1": "MyStudyCompanion-phone-0.14.2",
    "MyStudyCompanion-wear-0.14.0": "MyStudyCompanion-wear-0.14.2",
    "MyStudyCompanion-wear-0.14.1": "MyStudyCompanion-wear-0.14.2",
    "MyStudyCompanion-Web-0.14.0-PWA.zip": "MyStudyCompanion-Web-0.14.2-PWA.zip",
    "MyStudyCompanion-Web-0.14.1-PWA.zip": "MyStudyCompanion-Web-0.14.2-PWA.zip",
    "My Study Companion 0.14.0 Interactive Workbooks": "My Study Companion 0.14.2 Workbook Theme Repair",
    "My Study Companion 0.14.1 Unified Study Reader": "My Study Companion 0.14.2 Workbook Theme Repair",
}
for old, new in replacements.items():
    source = source.replace(old, new)

final_web_marker = 'msc-web-v0142-workbook-theme-release-v1'
# Split the legacy literals so earlier overlay scripts cannot accidentally
# rewrite this stale-build list while editing their own expected cache marker.
legacy_web_markers = (
    'msc-web-v0140-' + 'interactive-workbooks',
    'msc-web-v0141-' + 'unified-study-reader',
    'msc-web-v0142-' + 'complete-reader',
    'msc-web-v0143-' + 'theme-gallery',
    'msc-web-v0144-' + 'auth-theme-repair',
    'msc-web-v0145-' + 'static-theme-auth-repair-v2',
    'msc-web-v0145-' + 'static-theme-auth-repair',
)
for marker in legacy_web_markers:
    source = source.replace(marker, final_web_marker)

# Append one independent release gate. The compatibility line for firebase.json
# is intentionally retained as a stable insertion point for the live-release,
# theme, and production overlays reconstructed before this validator executes.
gate_tag = '# MSC_0142_FINAL_RELEASE_GATE'
if gate_tag not in source:
    source = source.rstrip() + r'''

# MSC_0142_FINAL_RELEASE_GATE
verify_msc_0142_final_release() {
  local reader_repo=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
  local reader_ui=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
  local auth=MyStudyCompanionWeb/firebase-sync.js
  local appearance=MyStudyCompanionWeb/appearance.js
  local app_theme=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt

  # Stable compatibility anchor used by the reconstructed live-stack overlays.
  grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json

  # Unified reader and connected content must still be present.
  grep -Fq 'OfficialDailyTextRepository' "$reader_repo"
  grep -Fq 'OfficialWatchtowerStudyRepository' "$reader_repo"
  grep -Fq 'OfficialPageReader' "$reader_repo"
  grep -Fq 'memberStudyMaterials' "$reader_repo"
  grep -Fq 'TextToSpeech' "$reader_ui"
  grep -Fq 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' "$reader_ui"
  grep -Fq 'toggleBookmark' "$reader_repo"
  grep -Fq 'revisionByDocument' "$reader_repo"

  # Restore the working Google sign-in lifecycle: durable local persistence,
  # redirect completion before state observation, direct popup result handling,
  # and redirect fallback only when the browser actually blocks the popup.
  grep -Fq 'browserLocalPersistence' "$auth"
  grep -Fq 'getRedirectResult' "$auth"
  grep -Fq 'const result = await modules.signInWithPopup' "$auth"
  grep -Fq 'error?.code === "auth/popup-blocked"' "$auth"
  grep -Fq 'signInWithRedirect' "$auth"
  grep -Fq 'msc-web-v0142-workbook-theme-release-v1' MyStudyCompanionWeb/sw.js

  # Validate the exact 25 permanent themes, the protected nine, all rebuilt
  # artwork copies, previews, and phone/Wear/web parity independently.
  python3 .github/scripts/verify-approved-theme-output.py

  test -z "$(find MyStudyCompanionWeb -type f \( -name '*.orig' -o -name '*.rej' \) -print -quit)"
  for file in MyStudyCompanionWeb/*.js; do node --check "$file"; done
  node --test \
    MyStudyCompanionWeb/appearance.test.mjs \
    MyStudyCompanionWeb/study-library-merge.test.mjs
}

verify_msc_0142_final_release
''' + '\n'

required = (
    "grep -q 'versionCode = 34'",
    '0.14.2-private-alpha-workbook-theme-repair',
    "grep -q 'versionCode = 360142001'",
    '0.14.2-wear-private-alpha-workbook-theme-repair',
    'MyStudyCompanion-phone-0.14.2',
    'MyStudyCompanion-wear-0.14.2',
    'MyStudyCompanion-Web-0.14.2-PWA.zip',
    "my-study-companion-private",
    final_web_marker,
    gate_tag,
    'OfficialWatchtowerStudyRepository',
    'browserLocalPersistence',
    'getRedirectResult',
    'auth/popup-blocked',
    'verify-approved-theme-output.py',
)
for marker in required:
    if marker not in source:
        raise SystemExit(f'Missing corrected 0.14.1 CI marker: {marker}')

stale = (
    "grep -q 'versionCode = 32'",
    "grep -q 'versionCode = 33'",
    "versionCode='32'",
    "versionCode='33'",
    '0.14.0-private-alpha-interactive-workbooks',
    "versionCode='360140001'",
    '0.14.0-wear-private-alpha-interactive-workbooks',
    'MyStudyCompanion-phone-0.14.0',
    'MyStudyCompanion-wear-0.14.0',
    'MyStudyCompanion-Web-0.14.0-PWA.zip',
    'MyStudyCompanion-Web-0.14.1-PWA.zip',
    *legacy_web_markers,
)
for marker in stale:
    if marker in source:
        raise SystemExit(f'Stale CI marker remains: {marker}')

path.write_text(source, encoding='utf-8')
print('Repaired the reconstructed 0.14.2 build gate with robust auth, exact-theme, reader, artifact, and stale-build validation.')
