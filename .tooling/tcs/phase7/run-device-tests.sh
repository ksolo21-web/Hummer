#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.6.1.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
adb shell svc power stayon true
capture_phase7() {
  adb shell run-as "$PKG" ls files > evidence/phase7-capture-paths.txt
  while IFS= read -r path; do
    path="${path//$'\r'/}"
    [[ "$path" == phase7-*.json || "$path" == phase7-*.png ]] || continue
    adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
  done < evidence/phase7-capture-paths.txt
}
adb logcat -b crash -c
timeout 1500 adb shell am instrument -w -r -e class "$PKG.Phase7RealImageIntakeInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase7-real-image-intake.log 2>&1 || true
adb logcat -b crash -d > evidence/phase7-crash.log
capture_phase7
python3 - <<'PY'
from pathlib import Path
import json
log=Path('evidence/phase7-real-image-intake.log').read_text()
for case in ['a265','296-conflict','unidentified','clipped']:
    p=Path(f'evidence/phase7-{case}-intake.json')
    assert p.exists(),f'Missing actual native intake evidence: {p}'
    j=json.loads(p.read_text())
    assert j['sourceSha256'] and j['inputKind']=='OUTLINED_AREA'
assert 'OK (5 tests)' in log,log
PY
# Verify the new review path on phone and wide layouts, in both actual app themes.
# These tests are not native-card acceptance evidence and cannot close Phase 7.
trap 'adb shell wm size reset >/dev/null 2>&1 || true; adb shell wm density reset >/dev/null 2>&1 || true' EXIT
for viewport in phone wide; do
  if [[ "$viewport" == wide ]]; then
    adb shell wm size 1920x1200
    adb shell wm density 160
  fi
  adb shell wm size > "evidence/phase7-finding-$viewport-display.txt"
  adb shell wm density >> "evidence/phase7-finding-$viewport-display.txt"
  adb logcat -b crash -c
  timeout 900 adb shell am instrument -w -r -e phase7Viewport "$viewport" -e class "$PKG.OutlinedFindingReviewInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/phase7-finding-$viewport.log" 2>&1 || true
  adb logcat -b crash -d > "evidence/phase7-finding-$viewport-crash.log"
  capture_phase7
  python3 - "$viewport" <<'PY'
from pathlib import Path
import json,sys
viewport=sys.argv[1]
log=Path(f'evidence/phase7-finding-{viewport}.log').read_text()
assert 'OK (4 tests)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in Path(f'evidence/phase7-finding-{viewport}-crash.log').read_text()
for name in ['persistence','light','dark','source-replacement']:
    p=Path(f'evidence/phase7-finding-{name}-{viewport}.json')
    j=json.loads(p.read_text())
    assert j['sourceSha256']=='8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d'
    assert j['testOnly'] is True and j['cardApproved'] is False
for theme in ['light','dark']:
    assert Path(f'evidence/phase7-finding-{theme}-{viewport}.png').stat().st_size>1000
PY
done
python3 - <<'PY'
from pathlib import Path
import json
Path('evidence/phase7-acceptance-status.json').write_text(json.dumps({
  'scope':'software_regression_tests_only',
  'phase7Complete':False,
  'nativeCardsApprovedByThisRun':0,
  'requiredRealImageCards':4,
  'intakeTestsPassed':5,
  'findingReviewTestsPassed':{'phone':4,'wide':4},
  'note':'No exact final native card PDFs, current-geometry acceptance, critic approval, or save/readback receipts were produced by this regression run.'
},indent=2)+'\n')
PY
