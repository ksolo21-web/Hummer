#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
mkdir -p evidence
adb install -r evidence/TerritoryCardStudio-Phase7-Finalize-debug.apk > evidence/finalize-app-install.log
adb install -r evidence/finalize-emulator-test.apk > evidence/finalize-test-install.log
adb shell svc power stayon true

[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Refusing emulator isolation on a physical device'; exit 1; }
LAUNCHER=com.google.android.apps.nexuslauncher
adb shell pm list packages "$LAUNCHER" > evidence/finalize-launcher-before.txt
restore_emulator() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell pm enable --user 0 "$LAUNCHER" >> evidence/finalize-launcher-restore.txt 2>&1 || true
}
trap restore_emulator EXIT
if grep -qx "package:$LAUNCHER" <(tr -d '\r' < evidence/finalize-launcher-before.txt); then
  adb shell pm disable-user --user 0 "$LAUNCHER" > evidence/finalize-launcher-isolation.txt
  adb shell am force-stop "$LAUNCHER" >> evidence/finalize-launcher-isolation.txt
fi

capture() {
  adb shell run-as "$PKG" ls files > evidence/finalize-capture-paths.txt
  while IFS= read -r path; do
    path="${path//$'\r'/}"
    [[ "$path" == phase7-ancillary-*.json || "$path" == phase7-ancillary-*.png ]] || continue
    adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
  done < evidence/finalize-capture-paths.txt
}

for viewport in phone wide; do
  if [[ "$viewport" == wide ]]; then
    adb shell wm size 1920x1200
    adb shell wm density 160
  fi
  adb shell wm size > "evidence/finalize-$viewport-display.txt"
  adb shell wm density >> "evidence/finalize-$viewport-display.txt"
  adb logcat -b crash -c
  adb logcat -b events -c
  timeout 900 adb shell am instrument -w -r -e phase7Viewport "$viewport"     -e class "$PKG.Phase7AncillaryUiInstrumentationTest"     "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/finalize-$viewport.log" 2>&1 || true
  adb logcat -b crash -d > "evidence/finalize-$viewport-crash.log"
  adb logcat -b events -d -s am_anr:I > "evidence/finalize-$viewport-anr.log"
  capture
  python3 - "$viewport" <<'PY'
from pathlib import Path
import hashlib,json,sys
viewport=sys.argv[1]
log=Path(f'evidence/finalize-{viewport}.log').read_text()
assert 'OK (2 tests)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in Path(f'evidence/finalize-{viewport}-crash.log').read_text()
assert 'com.koenterprises.territorycardstudio' not in Path(f'evidence/finalize-{viewport}-anr.log').read_text()
expected='com.koenterprises.territorycardstudio'
for theme in ['light','dark']:
    report=Path(f'evidence/phase7-ancillary-{theme}-{viewport}.json')
    assert report.exists(),report
    j=json.loads(report.read_text())
    assert j['testOnly'] is True and j['cardApproved'] is False
    assert j['conflictingFactsRetainedAndRejected'] is True
    assert j['explicitKindPersisted'] is True
    assert j['redResidenceUnchanged'] is True
    assert j['kindEditResetsConfirmation'] is True
    assert j['nativeRegistrationStillBlocked'] is True
    for stage in ['kind-edit','trace']:
        image=Path(f'evidence/phase7-ancillary-{theme}-{stage}-{viewport}.png')
        receipt=Path(f'evidence/phase7-ancillary-{theme}-{stage}-{viewport}-screen.json')
        assert image.stat().st_size>1000,image
        s=json.loads(receipt.read_text())
        assert s['accepted'] is True and s['cardApproved'] is False,receipt
        assert s['activePackageBefore']==s['activePackageAfter']==s['expectedPackage']==expected,receipt
        witnesses=[]
        for side in ['Before','After']:
            ws=[s.get('accessibilityPackage'+side),s.get('focusedWindowPackage'+side),s.get('resumedActivityPackage'+side)]
            ws=[w for w in ws if w is not None]
            assert ws and all(w==expected for w in ws),(receipt,side,ws)
        assert hashlib.sha256(image.read_bytes()).hexdigest()==s['screenshotSha256'],receipt
PY
done

python3 - <<'PY'
from pathlib import Path
import json
Path('evidence/phase7-finalize-status.json').write_text(json.dumps({
  'schema':'phase7-finalize-software-gate-v1',
  'scope':'label placement and explicit ancillary/context semantics',
  'coreContractTests':4,
  'androidTests':{'phone':2,'wide':2},
  'screenshots':8,
  'phase7Complete':False,
  'approvedRealCards':0,
  'note':'This proves the repaired editor/rendering behavior only. Real A265/269/A295/258 acceptance remains separate.'
},indent=2)+'\n')
PY
