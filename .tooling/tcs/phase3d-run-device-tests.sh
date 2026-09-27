#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/emulator-app.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
adb shell am force-stop com.google.android.apps.nexuslauncher
PKG=com.koenterprises.territorycardstudio
CLASS=$PKG.Phase3DIntegrationInstrumentationTest
UI=$PKG.Phase3DIntegrationUiInstrumentationTest
collect() {
 adb shell run-as "$PKG" ls files > evidence/capture-paths.txt 2>/dev/null || true
 while IFS= read -r path; do
  path="${path//$'\r'/}"
  [[ "$path" == phase3d-*.png || "$path" == phase3d-*.json || "$path" == phase3d-debug-*.txt ]] || continue
  adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
 done < evidence/capture-paths.txt
}
adb shell am instrument -w -r -e class "$CLASS#editedLocalApprovalNeverCreatesExportAuthorityOrChangesReservedCards,$CLASS#everyPostDecisionInvalidationRejectsOldTicketAndArtifacts" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase3d-retry.log 2>&1
collect
python3 .tooling/tcs/phase3d-summarize.py evidence
