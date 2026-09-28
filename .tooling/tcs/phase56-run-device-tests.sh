#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.6.0.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am force-stop com.google.android.apps.nexuslauncher
run() {
 timeout 300 adb shell am instrument -w -r -e class "$2" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/phase56-$1.log" 2>&1 || true
}
run output "$PKG.Phase56OutputInstrumentationTest#allModesStartUnpreparedAndExportExactApprovedPacketAndAudit,$PKG.Phase56OutputInstrumentationTest#unapprovedRejectedAndPartialPacketsCannotExport,$PKG.Phase56OutputInstrumentationTest#newVersionWithIdenticalBytesInvalidatesExport,$PKG.Phase56OutputInstrumentationTest#changedSourceAndSameCandidateRejectionInvalidateExport,$PKG.Phase56OutputInstrumentationTest#offlineFinalVerificationCannotReuseEarlierApproval,$PKG.Phase56OutputInstrumentationTest#expiredAndUnknownSessionTicketsFailClosed,$PKG.Phase56OutputInstrumentationTest#failedWritesAndCorruptReadbackDeleteNewDocument,$PKG.Phase56OutputInstrumentationTest#cleanupFailureReportsResidualFile,$PKG.Phase56OutputInstrumentationTest#auditWriteIsBoundToSameApprovalAndProviderEvidence,$PKG.Phase56OutputInstrumentationTest#registeredProjectCodecRoundTripAcrossModes,$PKG.Phase56OutputInstrumentationTest#arbitraryOrWrongModeProjectCannotCreateBaseline,$PKG.Phase56OutputInstrumentationTest#expiredImportAndSourceChangedDuringValidationCannotPrepare,$PKG.Phase56OutputInstrumentationTest#importedProviderClaimsDoNotReplaceFreshVerification,$PKG.Phase56OutputInstrumentationTest#projectTamperAfterPreparationInvalidatesApproval,$PKG.Phase56OutputInstrumentationTest#freshServiceCannotRestoreExportAuthority,$PKG.Phase56OutputInstrumentationTest#preparedBaselineSurvivesImportTicketExpiry,$PKG.Phase56OutputInstrumentationTest#repeatedProjectImportUsesOneDurableSlot,$PKG.Phase56OutputInstrumentationTest#renamedOutputFailsWithoutSaving,$PKG.Phase56OutputInstrumentationTest#realReplacementCardsRemainUncommissioned,$PKG.Phase56DocumentsInstrumentationTest,$PKG.Phase56UiInstrumentationTest"
run before-restart "$PKG.Phase56RestartInstrumentationTest#stageBeforeProcessDeath"
adb shell am force-stop "$PKG"
run after-restart "$PKG.Phase56RestartInstrumentationTest#recoverSavedProjectRebuildReapproveAndExportInNewProcess"
run affected "$PKG.Phase2GExportInstrumentationTest#exactApprovedAttachmentExportAndRestartPreserveEveryByte,$PKG.Phase2DBNavigationInstrumentationTest#realWorkspaceOpensBlockedBuildWithoutCommissioningTerritory"
adb shell wm size 2560x1600
adb shell wm density 240
run wide "$PKG.Phase56UiInstrumentationTest"
adb shell wm size reset
adb shell wm density reset
adb shell run-as "$PKG" ls files > evidence/capture-paths.txt 2>/dev/null || true
while IFS= read -r path; do
 path="${path//$'\r'/}"
 [[ "$path" == phase56-* ]] || continue
 adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
done < evidence/capture-paths.txt
python3 - <<'PY'
from pathlib import Path
import json
passed=[];failed=[];executions=[]
for name,count in [('output',22),('before-restart',1),('after-restart',1),('affected',2),('wide',2)]:
 text=Path(f'evidence/phase56-{name}.log').read_text();cls=test=None;good=[];bad=[]
 for line in text.splitlines():
  if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS: test='):test=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
   n=int(line.rsplit(' ',1)[1])
   if n<=0 and test:(good if n==0 else bad).append([cls,test]);cls=test=None
 passed+=good;failed+=bad;executions.append(dict(log=name,expected=count,passed=good,failed=bad))
Path('evidence/test-identities.json').write_text(json.dumps(dict(passed=passed,failed=failed,executions=executions),indent=2))
for e in executions:assert len(e['passed'])+len(e['failed'])==e['expected'],e
assert not failed,failed
PY
