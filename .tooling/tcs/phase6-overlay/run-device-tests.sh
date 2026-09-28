#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.6.1.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am force-stop com.google.android.apps.nexuslauncher
run() {
 adb logcat -b crash -c
 timeout 300 adb shell am instrument -w -r -e class "$2" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/phase6-$1.log" 2>&1 || true
 adb logcat -b crash -d > "evidence/phase6-$1-crash.log"
}
run native-regular "$PKG.Phase6NativeUiInstrumentationTest#fullRegularLight"
run native-picture "$PKG.Phase6NativeUiInstrumentationTest#pictureGeneratesNativeDraftAndExports"
run native-retention "$PKG.Phase6NativePersistenceInstrumentationTest#replacementFailurePreservesDraftAndArchivedSource"
run native-edits "$PKG.Phase6NativePersistenceInstrumentationTest#savedEditsInvalidateRegistrationAndPruningRetainsRegisteredEvidence"
run native-stage "$PKG.Phase6NativePersistenceInstrumentationTest#stageDraftForProcessDeath"
adb shell am force-stop "$PKG"
run native-recover "$PKG.Phase6NativePersistenceInstrumentationTest#recoverDraftAfterProcessDeathRequiresFreshPreparationAndApproval"
adb shell run-as "$PKG" ls files > evidence/capture-paths.txt 2>/dev/null || true
while IFS= read -r path; do
 path="${path//$'\r'/}"
 [[ "$path" == phase6-* ]] || continue
 adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
done < evidence/capture-paths.txt
python3 - <<'PY'
from pathlib import Path
import json
passed=[];failed=[];executions=[]
for name,count in [('native-regular',1),('native-picture',1),('native-retention',1),('native-edits',1),('native-stage',1),('native-recover',1)]:
 text=Path(f'evidence/phase6-{name}.log').read_text();cls=test=None;good=[];bad=[]
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

