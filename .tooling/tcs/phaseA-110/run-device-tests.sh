#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
OUT=evidence/phase-a-device
mkdir -p "$OUT"

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r evidence/Territory-PhaseA-1.1.0-debug.apk > "$OUT/install-app.log"
adb install -r evidence/Territory-PhaseA-1.1.0-androidTest.apk > "$OUT/install-tests.log"
adb logcat -b crash -c
adb logcat -b events -c

adb shell am instrument -w -r   -e class "$PKG.PhaseA110InstrumentationTest"   "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/phase-a-ui.log"
grep -q 'OK (4 tests)' "$OUT/phase-a-ui.log"

adb shell am instrument -w -r   -e class "$PKG.SimpleCreate101InstrumentationTest"   "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/simple-create-regression.log"
grep -q 'OK (2 tests)' "$OUT/simple-create-regression.log"

adb shell am instrument -w -r   -e class "$PKG.Phase7SourceSufficiencyInstrumentationTest#positiveSourceCompletesTheExistingApprovalAndExportLifecycle"   "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/source-ready-lifecycle.log"
grep -q 'OK (1 test)' "$OUT/source-ready-lifecycle.log"

for f in phaseA-home-light.png phaseA-create-multiselect.png phaseA-workspace-simple.png; do
  adb exec-out run-as "$PKG" cat "files/$f" > "$OUT/$f"
  test -s "$OUT/$f"
done

adb logcat -b crash -d > "$OUT/crash.log"
adb logcat -b events -d -s am_anr:I > "$OUT/anr.log"
python3 - "$OUT" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1])
crash=(root/"crash.log").read_text(errors="replace")
anr=(root/"anr.log").read_text(errors="replace")
assert "com.koenterprises.territorycardstudio" not in crash,crash
assert "com.koenterprises.territorycardstudio" not in anr,anr
shots={}
for p in sorted(root.glob("*.png")):
    assert p.stat().st_size>1000,p
    shots[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
(root/"status.json").write_text(json.dumps({
  "phaseAUiTests":4,
  "simpleCreateRegressionTests":2,
  "sourceReadyLifecycleTests":1,
  "multiSelectMinimum":1,
  "crashFree":True,
  "anrFree":True,
  "screenshots":shots
},indent=2)+"\n")
PY
