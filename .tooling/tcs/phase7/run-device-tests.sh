#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.6.1.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
adb shell svc power stayon true
adb logcat -b crash -c
timeout 1500 adb shell am instrument -w -r -e class "$PKG.Phase7RealImageIntakeInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase7-real-image-intake.log 2>&1 || true
adb logcat -b crash -d > evidence/phase7-crash.log
adb shell run-as "$PKG" ls files > evidence/phase7-capture-paths.txt
while IFS= read -r path; do
  path="${path//$'\r'/}"
  [[ "$path" == phase7-*-intake.json ]] || continue
  adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
done < evidence/phase7-capture-paths.txt
python3 - <<'PY'
from pathlib import Path
import json
log=Path('evidence/phase7-real-image-intake.log').read_text()
for case in ['a265','296-conflict','unidentified','clipped']:
    p=Path(f'evidence/phase7-{case}-intake.json')
    assert p.exists(),f'Missing actual native intake evidence: {p}'
    j=json.loads(p.read_text())
    assert j['sourceSha256'] and j['inputKind']=='OUTLINED_AREA'
assert 'OK (4 tests)' in log,log
PY
