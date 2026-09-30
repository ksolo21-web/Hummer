#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
OUT=evidence/phase-c-device
mkdir -p "$OUT"

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r evidence/Territory-PhaseC-1.3.0-debug.apk > "$OUT/install-app.log"
adb install -r evidence/Territory-PhaseC-1.3.0-androidTest.apk > "$OUT/install-tests.log"
adb logcat -b crash -c
adb logcat -b events -c

adb shell am instrument -w -r -e class "$PKG.PhaseC130InstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/phase-c-ui.log"
grep -q 'OK (5 tests)' "$OUT/phase-c-ui.log"

adb shell wm size 1920x1200
adb shell wm density 160
adb shell am instrument -w -r -e class "$PKG.PhaseC130InstrumentationTest#wideAdvancedScreenKeepsSimpleSecondaryHierarchy" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/phase-c-wide.log"
grep -q 'OK (1 test)' "$OUT/phase-c-wide.log"
adb shell am instrument -w -r -e class "$PKG.PhaseB120InstrumentationTest#wideDarkDetailPreservesSimpleHierarchy" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/phase-b-wide-regression.log"
grep -q 'OK (1 test)' "$OUT/phase-b-wide-regression.log"
adb shell wm size reset
adb shell wm density reset

adb shell am instrument -w -r -e class "$PKG.PhaseB120InstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/phase-b-regression.log"
grep -q 'OK (5 tests)' "$OUT/phase-b-regression.log"

adb shell am instrument -w -r -e class "$PKG.PhaseA110InstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/phase-a-regression.log"
grep -q 'OK (4 tests)' "$OUT/phase-a-regression.log"

adb shell am instrument -w -r -e class "$PKG.SimpleCreate101InstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/simple-create-regression.log"
grep -q 'OK (2 tests)' "$OUT/simple-create-regression.log"

adb shell am instrument -w -r -e class "$PKG.Phase7SourceSufficiencyInstrumentationTest#positiveSourceCompletesTheExistingApprovalAndExportLifecycle" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/source-ready-lifecycle.log"
grep -q 'OK (1 test)' "$OUT/source-ready-lifecycle.log"

for f in phaseC-more-light.png phaseC-advanced-light.png phaseC-advanced-dark.png phaseC-advanced-wide-dark.png; do
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
  "phaseCUiTests":5,
  "phaseCWideTests":1,
  "phaseBRegressionTests":5,
  "phaseBWideRegressionTests":1,
  "phaseARegressionTests":4,
  "simpleCreateRegressionTests":2,
  "sourceReadyLifecycleTests":1,
  "advancedToolsSecondary":True,
  "knowledgeBaseSecondary":True,
  "createFlowStillSimple":True,
  "crashFree":True,
  "anrFree":True,
  "screenshots":shots
},indent=2)+"\n")
PY
