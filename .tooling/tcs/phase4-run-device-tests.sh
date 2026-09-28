#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.4.0.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
CLASS=$PKG.Phase4LifecycleInstrumentationTest
UI=$PKG.Phase4LifecycleUiInstrumentationTest
adb shell am force-stop com.google.android.apps.nexuslauncher
collect() {
 adb shell run-as "$PKG" ls files > evidence/capture-paths.txt 2>/dev/null || true
 while IFS= read -r path; do
  path="${path//$'\r'/}"
  [[ "$path" == phase4-*.png || "$path" == phase4-*.json || "$path" == phase4-debug-*.txt ]] || continue
  adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
 done < evidence/capture-paths.txt
}
adb shell am instrument -w -r -e class "$CLASS#editedJournalAndAuthorityChangesSuspendApprovedCandidates" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase4-audit-retry.log 2>&1
collect
python3 .tooling/tcs/phase4-summarize.py evidence
