#!/usr/bin/env python3
from pathlib import Path

path = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = path.read_text(encoding='utf-8')

# Normalize the reconstructed 0.14.0 runner to the actual repaired 0.14.1
# identities. The reconstruction overlays can restore an older copy of this
# runner, so this repair must be idempotent and must not depend on one exact
# historical anchor.
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
}
for old, new in replacements.items():
    source = source.replace(old, new)

final_web_marker = 'msc-web-v0144-auth-theme-repair'
legacy_web_markers = (
    'msc-web-v0140-interactive-workbooks',
    'msc-web-v0141-unified-study-reader',
    'msc-web-v0142-complete-reader',
    'msc-web-v0143-theme-gallery',
)
for marker in legacy_web_markers:
    source = source.replace(marker, final_web_marker)

# Append one independent release gate. This avoids the previous brittle logic
# that tried to replace one exact old line and then failed before the real build
# even started. The gate runs after the runner's normal build path and verifies
# the actual reconstructed files, not merely patch text.
gate_tag = '# MSC_0141_AUTH_THEME_RELEASE_GATE'
if gate_tag not in source:
    source = source.rstrip() + r'''

# MSC_0141_AUTH_THEME_RELEASE_GATE
verify_msc_0141_auth_theme_repair() {
  local reader_repo=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt
  local reader_ui=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt
  local auth=MyStudyCompanionWeb/firebase-sync.js
  local appearance=MyStudyCompanionWeb/appearance.js
  local app_theme=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt

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
  grep -Fq 'msc-web-v0144-auth-theme-repair' MyStudyCompanionWeb/sw.js

  # Only the approved original and animal themes may ship.
  for approved in \
    'Calm Light' 'Premium Dark' 'Warm Editorial — White' \
    'Owl' 'Fox' 'Lion' 'Tiger' 'Moonlit Wolf' 'Golden Owl' 'Sakura Tiger' 'Automatic'; do
    grep -Fq "$approved" "$app_theme" || grep -Fq "$approved" "$appearance"
  done

  for rejected in \
    'Waterfall Serenity' 'Rainforest Harmony' 'Ocean Majesty' 'Celestial Wonder' \
    'Mountain Sunrise' 'Creation Garden' 'Bible Sketch Study' 'Parable Line Panels' \
    'Noah’s Ark' 'Red Sea Deliverance' 'Creation Sky' 'Bible Timeline' 'Bible Map'; do
    ! grep -R -F "$rejected" \
      MyStudyCompanion/app/src/main \
      MyStudyCompanion/wear/src/main \
      MyStudyCompanionWeb \
      --exclude='*.test.mjs' --exclude='*.md'
  done

  test -z "$(find MyStudyCompanionWeb -type f \( -name '*.orig' -o -name '*.rej' \) -print -quit)"
  for file in MyStudyCompanionWeb/*.js; do node --check "$file"; done
  node --test \
    MyStudyCompanionWeb/appearance.test.mjs \
    MyStudyCompanionWeb/study-library-merge.test.mjs
}

verify_msc_0141_auth_theme_repair
''' + '\n'

required = (
    "grep -q 'versionCode = 33'",
    '0.14.1-private-alpha-unified-study-reader',
    "grep -q 'versionCode = 360141001'",
    '0.14.1-wear-private-alpha-unified-study-reader',
    'MyStudyCompanion-phone-0.14.1',
    'MyStudyCompanion-wear-0.14.1',
    'MyStudyCompanion-Web-0.14.1-PWA.zip',
    final_web_marker,
    gate_tag,
    'OfficialWatchtowerStudyRepository',
    'browserLocalPersistence',
    'getRedirectResult',
    'auth/popup-blocked',
    'Waterfall Serenity',
)
for marker in required:
    if marker not in source:
        raise SystemExit(f'Missing corrected 0.14.1 CI marker: {marker}')

stale = (
    "grep -q 'versionCode = 32'",
    "versionCode='32'",
    '0.14.0-private-alpha-interactive-workbooks',
    "versionCode='360140001'",
    '0.14.0-wear-private-alpha-interactive-workbooks',
    'MyStudyCompanion-phone-0.14.0',
    'MyStudyCompanion-wear-0.14.0',
    'MyStudyCompanion-Web-0.14.0-PWA.zip',
    *legacy_web_markers,
)
for marker in stale:
    if marker in source:
        raise SystemExit(f'Stale CI marker remains: {marker}')

path.write_text(source, encoding='utf-8')
print('Repaired the reconstructed 0.14.1 build gate with robust auth, approved-theme, reader, artifact, and stale-build validation.')
