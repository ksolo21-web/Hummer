#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
CLASS="$PKG.Phase7SourceSufficiencyInstrumentationTest"
OUT=evidence/source-sufficiency
mkdir -p "$OUT"
adb install -r evidence/TerritoryCardStudio-Phase7-Source-Sufficiency-debug.apk > "$OUT/install-app.log"
adb install -r evidence/source-sufficiency-androidTest.apk > "$OUT/install-tests.log"
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Refusing to run on a physical device'; exit 1; }
adb shell svc power stayon true
LAUNCHER=com.google.android.apps.nexuslauncher
changed_launcher=false
adb shell pm list packages "$LAUNCHER" > "$OUT/launcher-before.txt"
adb shell pm list packages -d "$LAUNCHER" > "$OUT/launcher-disabled-before.txt"
restore() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  if [[ "$changed_launcher" == true ]]; then adb shell pm enable --user 0 "$LAUNCHER" >> "$OUT/launcher-restore.txt" 2>&1 || true; fi
}
trap restore EXIT
if grep -qx "package:$LAUNCHER" <(tr -d '\r' < "$OUT/launcher-before.txt") && ! grep -qx "package:$LAUNCHER" <(tr -d '\r' < "$OUT/launcher-disabled-before.txt"); then
  adb shell pm disable-user --user 0 "$LAUNCHER" > "$OUT/launcher-isolation.txt"
  adb shell am force-stop "$LAUNCHER"
  changed_launcher=true
fi
capture() {
  adb shell run-as "$PKG" ls files > "$OUT/files.txt"
  while IFS= read -r name; do
    name="${name//$'\r'/}"
    [[ "$name" == phase7-source-* ]] || continue
    [[ "$name" != */* ]] || continue
    adb exec-out run-as "$PKG" cat "files/$name" > "$OUT/$name"
  done < "$OUT/files.txt"
}
# Remove only this suite's evidence, never user data or prior approvals.
adb shell run-as "$PKG" ls files > "$OUT/stale-files.txt"
while IFS= read -r name; do
  name="${name//$'\r'/}"
  [[ "$name" == phase7-source-* && "$name" != */* ]] || continue
  adb shell run-as "$PKG" rm "files/$name"
done < "$OUT/stale-files.txt"
run_case() {
  local label="$1" count="$2" classes="$3" view="$4" code=0
  adb logcat -b crash -c
  adb logcat -b events -c
  timeout 1200 adb shell am instrument -w -r -e phase7Viewport "$view" -e class "$classes" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$label.log" 2>&1 || code=$?
  adb logcat -b crash -d > "$OUT/$label-crash.log"
  adb logcat -b events -d -s am_anr:I > "$OUT/$label-anr.log"
  capture
  python3 - "$OUT" "$label" "$count" "$code" <<'PY'
from pathlib import Path
import sys
out=Path(sys.argv[1]); label=sys.argv[2]; n=int(sys.argv[3]); code=int(sys.argv[4])
log=(out/f'{label}.log').read_text()
assert code==0,(label,code,log)
assert f'OK ({n} test'+(')' if n==1 else 's)') in log,log
assert 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log,log
assert 'FATAL EXCEPTION' not in (out/f'{label}-crash.log').read_text()
assert 'com.koenterprises.territorycardstudio' not in (out/f'{label}-anr.log').read_text()
PY
}
methods=(positiveSourceCompletesTheExistingApprovalAndExportLifecycle knownHoldCannotBypassRegistrationPreparationOrAnEarlierApproval processingFailureIsNotCalledInsufficientMapEvidence automaticFindingsCannotBeRemovedBySwitchingToManualDraft letterAndTelephonePacketsRequireTheirSeparateSources replacingSourceInvalidatesTheBoundAssessment fourActualUploadedMapsReceiveIndependentEvidenceBoundDecisions lightThemeSourceHoldIsReadable darkThemeSourceHoldIsReadable)
classes=""
for method in "${methods[@]}"; do classes+="${classes:+,}$CLASS#$method"; done
failure=0
adb shell wm size 1080x2400; adb shell wm density 420
if ! run_case phone 9 "$classes" phone; then failure=1; fi
if ! run_case restart-stage 1 "$CLASS#stageSourceHoldForProcessRestart" phone; then failure=1; fi
adb shell am force-stop "$PKG"
if ! run_case restart-consume 1 "$CLASS#sourceHoldSurvivesARealProcessRestart" phone; then failure=1; fi
adb shell wm size 1920x1200; adb shell wm density 160
if ! run_case wide 2 "$CLASS#lightThemeSourceHoldIsReadable,$CLASS#darkThemeSourceHoldIsReadable" wide; then failure=1; fi
[[ "$failure" == 0 ]] || exit 1
python3 - "$OUT" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1]); expected='com.koenterprises.territorycardstudio'
def read(name):return json.loads((root/f'phase7-source-{name}.json').read_text())
p=read('positive-control'); assert p['sourceReady'] and p['testOnly'] and p['syntheticControlApprovedAfterExplicitReview']
assert p['fieldCardApproved'] is False
assert hashlib.sha256((root/'phase7-source-positive-control.pdf').read_bytes()).hexdigest()==p['exportReadbackSha256']
n=read('no-bypass');assert n['priorApprovalDenied'] and n['draftCheckboxCannotOverrideHold'] and n['storeReadbackMatched'] and not n['sourceReady']
assert read('processing-error')['status']=='PROCESSING_ERROR'
assert read('source-replacement')['oldAssessmentInvalidated']
r=read('restart-proof'); assert r['producerPid']!=r['consumerPid'] and r['status']=='NEEDS_SOURCE'
b=read('actual-map-batch'); assert b['assessed']==4 and b['fieldCardsApproved']==0 and len(b['cases'])==4
assert len({c['sourceSha256'] for c in b['cases']})==4
for c in b['cases']:
    assert c['actualUploadedMap'] and not c['sourceReady'] and not c['fieldCardApproved']
    assert c['status'] in ['NEEDS_REVIEW','NEEDS_SOURCE','SOURCE_CONFLICT'],c
    assert c['blockers'] and all(x['reason'] and x['minimumEvidence'] and x['sourceSha256'] for x in c['blockers'])
for view in ['phone','wide']:
    for theme in ['light','dark']:
        stem=f'phase7-source-{theme}-{view}'
        report=read(f'{theme}-{view}');assert report['status']=='NEEDS_SOURCE' and report['testOnly']
        image=root/(stem+'.png');w=json.loads((root/(stem+'-screen.json')).read_text())
        assert w['schema']=='phase7-screen-evidence-v3' and w['accepted'] is True and w['cardApproved'] is False
        assert w['activePackageBefore']==w['activePackageAfter']==w['expectedPackage']==expected
        for side in ['Before','After']:
            present=[w.get(k+side) for k in ['accessibilityPackage','focusedWindowPackage','resumedActivityPackage'] if w.get(k+side)]
            assert present and all(x==expected for x in present)
            assert w.get('focusedWindowPackage'+side)==expected or (w.get('accessibilityPackage'+side)==expected and w.get('resumedActivityPackage'+side)==expected)
        assert hashlib.sha256(image.read_bytes()).hexdigest()==w['screenshotSha256']
(root/'verified-component-status.json').write_text(json.dumps({'nativeTests':13,'actualSourceCases':4,'sourceScreenshots':4,'syntheticLifecycleControls':1,'approvedRealCards':0,'phase7Complete':False,'internalVisualReviewRequired':True,'independentReviewNotImplied':True},indent=2))
PY
