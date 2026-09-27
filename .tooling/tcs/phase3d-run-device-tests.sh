#!/usr/bin/env bash
set -euo pipefail
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
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
adb shell am instrument -w -r -e class "$CLASS#editedBothModesReachExactPreviewAndExplicitReview,$CLASS#everyPostDecisionInvalidationRejectsOldTicketAndArtifacts,$CLASS#sameByteRebuildInvalidatesLocalDecisionInBothModes,$CLASS#editedLocalApprovalNeverCreatesExportAuthorityOrChangesReservedCards,$UI" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase3d-main.log 2>&1
collect
adb shell am instrument -w -r -e class "$CLASS#stageEditedDecisionsAndApprovedReferenceBeforeProcessDeath" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase3d-before-restart.log 2>&1
adb shell am force-stop "$PKG"
adb shell am instrument -w -r -e class "$CLASS#processRestartKeepsEditedSessionsBlockedAndApprovedReferenceExportWorking" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase3d-after-restart.log 2>&1
collect
adb shell wm size 1920x1200
adb shell wm density 160
adb shell am force-stop "$PKG"
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell am instrument -w -r -e class "$UI" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase3d-wide.log 2>&1
collect
python3 .tooling/tcs/phase3d-summarize.py evidence
