#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
OUT=evidence/source-full-ui
mkdir -p "$OUT"
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Only an isolated emulator is permitted'; exit 1; }
adb install -r evidence/TerritoryCardStudio-Phase7-Source-Sufficiency-debug.apk > "$OUT/install-app.log"
adb install -r evidence/source-sufficiency-androidTest.apk > "$OUT/install-tests.log"
adb shell run-as "$PKG" mkdir -p files
LAUNCHER=com.google.android.apps.nexuslauncher
changed=false
adb shell pm list packages "$LAUNCHER" > "$OUT/launcher-before.txt"
adb shell pm list packages -d "$LAUNCHER" > "$OUT/launcher-disabled-before.txt"
restore(){ adb shell wm size reset >/dev/null 2>&1 || true; adb shell wm density reset >/dev/null 2>&1 || true; if [[ "$changed" == true ]]; then adb shell pm enable --user 0 "$LAUNCHER" >/dev/null 2>&1 || true; fi; }
trap restore EXIT
if grep -qx "package:$LAUNCHER" <(tr -d '\r' < "$OUT/launcher-before.txt") && ! grep -qx "package:$LAUNCHER" <(tr -d '\r' < "$OUT/launcher-disabled-before.txt"); then
  adb shell pm disable-user --user 0 "$LAUNCHER" > "$OUT/launcher-isolation.txt"
  adb shell am force-stop "$LAUNCHER"
  changed=true
fi
run_case(){
  local name="$1" method="$2" pdfstem="$3" view="$4" code=0
  mkdir -p "$OUT/$name"
  adb shell run-as "$PKG" ls files > "$OUT/$name/before-files.txt"
  while IFS= read -r file; do
    file="${file//$'\r'/}"
    [[ "$file" == phase6-* && "$file" != */* ]] || continue
    adb shell run-as "$PKG" rm "files/$file"
  done < "$OUT/$name/before-files.txt"
  adb logcat -b crash -c; adb logcat -b events -c
  timeout 900 adb shell am instrument -w -r -e phase7Viewport "$view" -e class "$PKG.Phase6NativeUiInstrumentationTest#$method" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$name/test.log" 2>&1 || code=$?
  adb logcat -b crash -d > "$OUT/$name/crash.log"
  adb logcat -b events -d -s am_anr:I > "$OUT/$name/anr.log"
  adb shell run-as "$PKG" ls files > "$OUT/$name/after-files.txt"
  while IFS= read -r file; do
    file="${file//$'\r'/}"
    [[ "$file" == phase6-* && "$file" != */* ]] || continue
    adb exec-out run-as "$PKG" cat "files/$file" > "$OUT/$name/$file"
  done < "$OUT/$name/after-files.txt"
  python3 - "$OUT/$name" "$code" "$pdfstem" "$view" <<'PY'
from pathlib import Path
import hashlib,json,sys
out=Path(sys.argv[1]);code=int(sys.argv[2]);stem=sys.argv[3];view=sys.argv[4]
log=(out/'test.log').read_text()
assert code==0,(code,log)
assert 'OK (1 test)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in (out/'crash.log').read_text()
assert 'com.koenterprises.territorycardstudio' not in (out/'anr.log').read_text()
pdf=out/f'phase6-{stem}.pdf';audit=json.loads((out/f'phase6-{stem}-audit.json').read_text())
assert hashlib.sha256(pdf.read_bytes()).hexdigest()==audit['pdfSha256']
assert audit['assignmentAuthorization']=='explicit_local_user_reconciliation'
assert audit['nativeReconciliation']['registrationId']
if 'automatic' in stem: assert audit['nativeReconciliation']['imageInterpretationSha256']
images=list(out.glob('phase6-*.png'));assert len(images)>=6
(out/'test-evidence-index.json').write_text(json.dumps({'testOnly':True,'realCardApproved':False,'viewport':view,'pdfSha256':audit['pdfSha256'],'automaticImageIntake':'automatic' in stem,'nativeUiExplicitReviewAndSafReadback':True,'screenshots':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in images},'visualReviewStillRequired':True},indent=2))
PY
}
failure=0
adb shell wm size 1080x2400; adb shell wm density 420
if ! run_case automatic-phone pictureGeneratesNativeDraftAndExports regular-light-automatic phone; then failure=1; fi
if ! run_case regular-dark fullRegularDark regular-dark phone; then failure=1; fi
if ! run_case letter-dark fullLetterDark letter_writing-dark phone; then failure=1; fi
if ! run_case telephone-light fullTelephoneLight telephone-light phone; then failure=1; fi
adb shell wm size 1920x1200; adb shell wm density 160
if ! run_case automatic-wide pictureGeneratesNativeDraftAndExports regular-light-automatic wide; then failure=1; fi
[[ "$failure" == 0 ]]
