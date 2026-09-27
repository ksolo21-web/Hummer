#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/android-test-results evidence/ui
frozen=.tooling/tcs/phase2k-evidence
cp "$frozen/frozen-run36323755251-pass62.xml" evidence/android-test-results/TEST-frozen-pass62.xml
cp "$frozen/TEST-retried-phase2k-capture.xml" evidence/android-test-results/
cp "$frozen/connected-test-status.txt" "$frozen/connected-android-test.log" evidence/
cp "$frozen"/retry-*.log evidence/
printf 'PASS62_SOURCE_RUN=36323755251\nPASS2_SOURCE_RUN=36328681508\nCURRENT_RUN=VISUAL_CAPTURE_AND_PACKAGE_ONLY\nPRIOR_PHASE2J_VISUALS=ACCEPTED_RUN36310455841_NOT_RECAPTURED\n' > evidence/frozen-provenance.txt
(cd tcs-src && gradle --no-daemon :app:assembleDebugAndroidTest) > evidence/android-test-package.log 2>&1
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
for mode in phone wide; do
  prefix=''
  if [ "$mode" = wide ]; then
    adb shell wm size 1920x1200
    adb shell wm density 160
    prefix='wide-'
  fi
  for theme in light dark; do
    method=captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes
    if [ "$theme" = dark ]; then method=captureFindingsAndAuditAndNavigateFromPreviewDark; fi
    adb shell am force-stop com.koenterprises.territorycardstudio
    log="evidence/visual-$mode-$theme.log"
    adb shell am instrument -w -r -e class "com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest#$method" com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1
    for page in findings audit; do
      name="phase2k-${prefix}${page}-${theme}.png"
      adb exec-out run-as com.koenterprises.territorycardstudio cat "files/$name" > "evidence/ui/$name" || true
    done
    grep -F 'OK (1 test)' "$log" >/dev/null
  done
done
