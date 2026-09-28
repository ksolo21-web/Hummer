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
adb shell am instrument -w -r -e class "$PKG.Phase56OutputInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase56-output.log 2>&1
adb shell run-as "$PKG" ls files > evidence/capture-paths.txt 2>/dev/null || true
while IFS= read -r path; do
 path="${path//$'\r'/}"
 [[ "$path" == phase56-* ]] || continue
 adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
done < evidence/capture-paths.txt
python3 - <<'PY'
from pathlib import Path
import json
p=Path('evidence/phase56-output.log');text=p.read_text();cls=name=None;passed=[];failed=[]
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
 if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
 if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
  n=int(line.rsplit(' ',1)[1])
  if n<=0 and name:(passed if n==0 else failed).append([cls,name]);cls=name=None
Path('evidence/test-identities.json').write_text(json.dumps(dict(passed=passed,failed=failed),indent=2))
assert len(passed)==17 and not failed,(passed,failed,text[-10000:])
PY
