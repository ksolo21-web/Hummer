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
 adb logcat -b crash -c
 timeout 300 adb shell am instrument -w -r -e class "$2" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/phase56-$1.log" 2>&1 || true
 adb logcat -b crash -d > "evidence/phase56-$1-crash.log"
}
# Round4: freeze all 27 passing executions. Only failed/new checks run here.
run documents "$PKG.Phase56DocumentsInstrumentationTest"
run receipt-race "$PKG.Phase56CodecRegressionInstrumentationTest#invalidationAfterReceiptCreationRemovesSuccessRecordAndDestination"
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
for name,count in [('documents',1),('receipt-race',1)]:
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
