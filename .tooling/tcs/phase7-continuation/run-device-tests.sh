#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
OUT=evidence/continuation
mkdir -p "$OUT"
APP=evidence/TerritoryCardStudio-Phase7-Continuation-debug.apk
TEST=evidence/TerritoryCardStudio-Phase7-Continuation-androidTest.apk
adb install -r "$APP" > "$OUT/install-app.log"
adb install -r "$TEST" > "$OUT/install-tests.log"
adb shell svc power stayon true
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Refusing Phase 7 continuation on a physical device'; exit 1; }
LAUNCHER=com.google.android.apps.nexuslauncher
adb shell pm list packages "$LAUNCHER" > "$OUT/launcher-before.txt"
restore() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell pm enable --user 0 "$LAUNCHER" >> "$OUT/launcher-restore.txt" 2>&1 || true
}
trap restore EXIT
if grep -qx "package:$LAUNCHER" <(tr -d '\r' < "$OUT/launcher-before.txt"); then
  adb shell pm disable-user --user 0 "$LAUNCHER" > "$OUT/launcher-isolation.txt"
  adb shell am force-stop "$LAUNCHER" >> "$OUT/launcher-isolation.txt"
fi
capture() {
  adb shell run-as "$PKG" ls files > "$OUT/file-list.txt"
  while IFS= read -r path; do
    path="${path//$'\r'/}"
    [[ "$path" == phase7-ancillary-*.json || "$path" == phase7-ancillary-*.png ]] || continue
    adb exec-out run-as "$PKG" cat "files/$path" > "$OUT/${path##*/}"
  done < "$OUT/file-list.txt"
}
for viewport in phone wide; do
  if [[ "$viewport" == phone ]]; then adb shell wm size 1080x2400; adb shell wm density 420
  else adb shell wm size 1920x1200; adb shell wm density 160; fi
  adb shell wm size > "$OUT/$viewport-display.txt"
  adb shell wm density >> "$OUT/$viewport-display.txt"
  adb logcat -b crash -c
  adb logcat -b events -c
  timeout 900 adb shell am instrument -w -r -e phase7Viewport "$viewport" -e class "$PKG.Phase7AncillaryUiInstrumentationTest" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$viewport.log" 2>&1 || true
  adb logcat -b crash -d > "$OUT/$viewport-crash.log"
  adb logcat -b events -d -s am_anr:I > "$OUT/$viewport-anr.log"
  capture
  python3 - "$OUT" "$viewport" <<'PY'
from pathlib import Path
import hashlib,json,sys
out=Path(sys.argv[1]); view=sys.argv[2]
log=(out/f'{view}.log').read_text()
assert 'OK (2 tests)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in (out/f'{view}-crash.log').read_text()
assert 'com.koenterprises.territorycardstudio' not in (out/f'{view}-anr.log').read_text()
for theme in ['light','dark']:
    report=json.loads((out/f'phase7-ancillary-{theme}-{view}.json').read_text())
    assert report['testOnly'] is True and report['cardApproved'] is False
    for key in ['conflictingFactsRetainedAndRejected','explicitKindPersisted','redResidenceUnchanged','kindEditResetsConfirmation','nativeRegistrationStillBlocked']:
        assert report[key] is True,(theme,view,key)
    for state in ['kind-edit','trace']:
        image=out/f'phase7-ancillary-{theme}-{state}-{view}.png'
        receipt=json.loads(image.with_name(image.stem+'-screen.json').read_text())
        assert receipt['schema']=='phase7-screen-evidence-v3'
        assert receipt['accepted'] is True and receipt['cardApproved'] is False
        assert receipt['activePackageBefore']==receipt['activePackageAfter']==receipt['expectedPackage']=='com.koenterprises.territorycardstudio'
        for side in ['Before','After']:
            witnesses=[receipt.get(k+side) for k in ['accessibilityPackage','focusedWindowPackage','resumedActivityPackage']]
            present=[w for w in witnesses if w is not None]
            assert present and all(w==receipt['expectedPackage'] for w in present),(image,side,witnesses)
            assert receipt.get('focusedWindowPackage'+side)==receipt['expectedPackage'] or (
                receipt.get('accessibilityPackage'+side)==receipt['expectedPackage'] and receipt.get('resumedActivityPackage'+side)==receipt['expectedPackage'])
        assert hashlib.sha256(image.read_bytes()).hexdigest()==receipt['screenshotSha256']
PY
done
python3 - <<'PY'
from pathlib import Path
import json
Path('evidence/continuation/status.json').write_text(json.dumps({
 'schema':'phase7-continuation-component-v1',
 'coreAndCompileRequired':True,
 'phoneTests':2,'wideTests':2,'screenshots':8,
 'phase7Complete':False,'approvedRealCards':0,
 'scope':'label placement and explicit ancillary building semantics; not real-card acceptance'
},indent=2)+'\n')
PY
