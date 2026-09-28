#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.6.1.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
adb shell svc power stayon true
# Test-emulator isolation only. A Pixel Launcher ANR covered otherwise passing
# Compose assertions in an earlier run. Disable only the emulator's launcher,
# retain that setup record, and restore it at exit. Never suppress target-app ANRs.
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Refusing launcher isolation on a physical device'; exit 1; }
LAUNCHER=com.google.android.apps.nexuslauncher
adb shell pm list packages "$LAUNCHER" > evidence/phase7-launcher-before.txt
restore_emulator() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell pm enable --user 0 "$LAUNCHER" >> evidence/phase7-launcher-restore.txt 2>&1 || true
}
trap restore_emulator EXIT
if grep -qx "package:$LAUNCHER" <(tr -d '\r' < evidence/phase7-launcher-before.txt); then
  adb shell pm disable-user --user 0 "$LAUNCHER" > evidence/phase7-launcher-isolation.txt
  adb shell am force-stop "$LAUNCHER" >> evidence/phase7-launcher-isolation.txt
fi
capture_phase7() {
  adb shell run-as "$PKG" ls files > evidence/phase7-capture-paths.txt
  while IFS= read -r path; do
    path="${path//$'\r'/}"
    [[ "$path" == phase7-*.json || "$path" == phase7-*.png ]] || continue
    adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
  done < evidence/phase7-capture-paths.txt
}
verify_screens() {
  python3 - "$1" "$2" <<'PY'
from pathlib import Path
import json,sys,hashlib
prefix,expected=sys.argv[1],int(sys.argv[2])
receipts=list(Path('evidence').glob(prefix+'*-screen.json'))
assert len(receipts)==expected,(prefix,len(receipts),expected)
for path in receipts:
    j=json.loads(path.read_text());image=path.with_name(path.name.replace('-screen.json','.png'))
    assert j['accepted'] is True and j['cardApproved'] is False,path
    assert j['activePackageBefore']==j['activePackageAfter']==j['expectedPackage']=='com.koenterprises.territorycardstudio',path
    for side in ['Before','After']:
        witnesses=[j.get('accessibilityPackage'+side),j.get('focusedWindowPackage'+side)]
        witnesses=[w for w in witnesses if w is not None]
        assert witnesses and all(w==j['expectedPackage'] for w in witnesses),(path,side,witnesses)
    assert hashlib.sha256(image.read_bytes()).hexdigest()==j['screenshotSha256'],path
PY
}
adb logcat -b crash -c
adb logcat -b events -c
timeout 1500 adb shell am instrument -w -r -e class "$PKG.Phase7RealImageIntakeInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/phase7-real-image-intake.log 2>&1 || true
adb logcat -b crash -d > evidence/phase7-crash.log
adb logcat -b events -d -s am_anr:I > evidence/phase7-intake-anr.log
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
assert 'com.koenterprises.territorycardstudio' not in Path('evidence/phase7-intake-anr.log').read_text()
PY
# Verify source review and real existing-territory commissioning on phone and wide
# layouts, in both actual app themes. Neither suite is passing-card evidence.
for viewport in phone wide; do
  if [[ "$viewport" == wide ]]; then
    adb shell wm size 1920x1200
    adb shell wm density 160
  fi
  adb shell wm size > "evidence/phase7-$viewport-display.txt"
  adb shell wm density >> "evidence/phase7-$viewport-display.txt"
  adb logcat -b crash -c
  adb logcat -b events -c
  timeout 900 adb shell am instrument -w -r -e phase7Viewport "$viewport" -e class "$PKG.OutlinedFindingReviewInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/phase7-finding-$viewport.log" 2>&1 || true
  adb logcat -b crash -d > "evidence/phase7-finding-$viewport-crash.log"
  adb logcat -b events -d -s am_anr:I > "evidence/phase7-finding-$viewport-anr.log"
  capture_phase7
  python3 - "$viewport" <<'PY'
from pathlib import Path
import json,sys
viewport=sys.argv[1]
log=Path(f'evidence/phase7-finding-{viewport}.log').read_text()
assert 'OK (4 tests)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in Path(f'evidence/phase7-finding-{viewport}-crash.log').read_text()
assert 'com.koenterprises.territorycardstudio' not in Path(f'evidence/phase7-finding-{viewport}-anr.log').read_text()
for name in ['persistence','light','dark','source-replacement']:
    j=json.loads(Path(f'evidence/phase7-finding-{name}-{viewport}.json').read_text())
    assert j['sourceSha256']=='8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d'
    assert j['testOnly'] is True and j['cardApproved'] is False
for theme in ['light','dark']:
    assert Path(f'evidence/phase7-finding-{theme}-{viewport}.png').stat().st_size>1000
PY
  verify_screens "phase7-finding-*-$viewport" 2
  adb logcat -b crash -c
  adb logcat -b events -c
  timeout 1000 adb shell am instrument -w -r -e phase7Viewport "$viewport" -e class "$PKG.OutlinedCommissioningInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/phase7-commissioning-$viewport.log" 2>&1 || true
  adb logcat -b crash -d > "evidence/phase7-commissioning-$viewport-crash.log"
  adb logcat -b events -d -s am_anr:I > "evidence/phase7-commissioning-$viewport-anr.log"
  capture_phase7
  python3 - "$viewport" <<'PY'
from pathlib import Path
import json,sys
viewport=sys.argv[1]
log=Path(f'evidence/phase7-commissioning-{viewport}.log').read_text()
assert 'OK (6 tests)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in Path(f'evidence/phase7-commissioning-{viewport}-crash.log').read_text()
assert 'com.koenterprises.territorycardstudio' not in Path(f'evidence/phase7-commissioning-{viewport}-anr.log').read_text()
for name in ['persistence','invalidation','authorization','atomic-history','light','dark']:
    j=json.loads(Path(f'evidence/phase7-commissioning-{name}-{viewport}.json').read_text())
    assert j['sourceSha256']=='8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d'
    assert j['territory']=='A265' and j['testOnly'] is True and j['cardApproved'] is False
for theme in ['light','dark']:
    for stage in ['confirmation','candidate','generated-draft','source-recovery']:
        assert Path(f'evidence/phase7-commissioning-{theme}-{stage}-{viewport}.png').stat().st_size>1000
PY
  verify_screens "phase7-commissioning-*-$viewport" 8
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
  'commissioningTestsPassed':{'phone':6,'wide':6},
  'activeWindowBoundScreenshots':20,
  'note':'Real A265 can enter an explicitly commissioned, isolated unapproved workspace, generate its source draft and save it. This is not final-card acceptance. No four exact final native card PDFs, current-geometry acceptance, critic approvals or final-card save/readback receipts were produced by this regression run.'
},indent=2)+'\n')
PY
