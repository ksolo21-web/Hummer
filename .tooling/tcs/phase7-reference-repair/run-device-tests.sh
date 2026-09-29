#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
adb install -r evidence/TerritoryCardStudio-Phase7-Reference-Repair-debug.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
adb shell svc power stayon true
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'This harness requires an isolated emulator'; exit 1; }
LAUNCHER=com.google.android.apps.nexuslauncher
adb shell pm list packages "$LAUNCHER" > evidence/launcher-before.txt
restore() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell pm enable --user 0 "$LAUNCHER" > evidence/launcher-restored.txt 2>&1 || true
}
trap restore EXIT
if grep -qx "package:$LAUNCHER" <(tr -d '\r' < evidence/launcher-before.txt); then
  adb shell pm disable-user --user 0 "$LAUNCHER" > evidence/launcher-isolated.txt
  adb shell am force-stop "$LAUNCHER" >> evidence/launcher-isolated.txt
fi
for viewport in phone wide; do
  if [[ "$viewport" == wide ]]; then adb shell wm size 1920x1200; adb shell wm density 160; fi
  adb shell wm size > "evidence/display-$viewport.txt"
  adb shell wm density >> "evidence/display-$viewport.txt"
  adb logcat -b crash -c; adb logcat -b events -c
  timeout 600 adb shell am instrument -w -r -e phase7Viewport "$viewport" -e class "$PKG.Phase7ReferenceComparisonInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "evidence/reference-$viewport.log" 2>&1 || true
  adb logcat -b crash -d > "evidence/reference-$viewport-crash.log"
  adb logcat -b events -d -s am_anr:I > "evidence/reference-$viewport-anr.log"
  adb shell run-as "$PKG" ls files > evidence/capture-paths.txt
  while IFS= read -r name; do
    name="${name//$'\r'/}"
    [[ "$name" == phase7-reference-comparison-*.json || "$name" == phase7-reference-comparison-*.png ]] || continue
    adb exec-out run-as "$PKG" cat "files/$name" > "evidence/$name"
  done < evidence/capture-paths.txt
  python3 - "$viewport" <<'PY'
from pathlib import Path
import json,sys,hashlib
view=sys.argv[1]; root=Path('evidence'); expected='com.koenterprises.territorycardstudio'
log=(root/f'reference-{view}.log').read_text()
assert 'OK (2 tests)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in (root/f'reference-{view}-crash.log').read_text()
assert expected not in (root/f'reference-{view}-anr.log').read_text()
for theme in ['light','dark']:
    stem=f'phase7-reference-comparison-{theme}-{view}'
    report=json.loads((root/(stem+'.json')).read_text())
    assert report['sourceSha256']=='8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d'
    assert report['territory']=='A265' and report['buildingCount']==6 and report['punctuationChanges']>0
    assert all(report[x] is True for x in ['testOnly','exactDraftReadback','registrationBlocked','originalUnchanged'])
    assert report['cardApproved'] is False
    witness=json.loads((root/(stem+'-screen.json')).read_text())
    assert witness['accepted'] is True and witness['cardApproved'] is False
    assert witness['activePackageBefore']==witness['activePackageAfter']==witness['expectedPackage']==expected
    for side in ['Before','After']:
        observed=[witness.get(k+side) for k in ['accessibilityPackage','focusedWindowPackage','resumedActivityPackage']]
        assert any(observed) and all(x==expected for x in observed if x is not None)
        assert witness.get('focusedWindowPackage'+side)==expected or (witness.get('accessibilityPackage'+side)==expected and witness.get('resumedActivityPackage'+side)==expected)
    assert hashlib.sha256((root/(stem+'.png')).read_bytes()).hexdigest()==witness['screenshotSha256']
PY
done
printf '%s\n' '{"schema":"phase7-reference-component-v2","scope":"reference_comparison_software_repair_only","overallPhase7Status":"see phase7-acceptance-status.json","forcedRealCardQuota":false,"approvedRealCardsByThisComponent":0,"androidCases":4,"screenshots":4,"componentPassed":true}' > evidence/reference-component-status.json
