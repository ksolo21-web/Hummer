#!/usr/bin/env bash
set -euo pipefail

(cd tcs-src && gradle --no-daemon --build-cache :core:test --rerun) > evidence/core.log 2>&1 || { cat evidence/core.log; exit 1; }
cp -r tcs-src/core/build/test-results/test evidence/core-results

python3 - <<'PY'
from pathlib import Path
import json, xml.etree.ElementTree as ET
root=Path("evidence/core-results")
files=list(root.rglob("TEST-*.xml"))
assert files, "No core JUnit XML found"
tests=failures=errors=skipped=0
classes=set()
for p in files:
    r=ET.parse(p).getroot()
    tests += int(r.attrib.get("tests",0))
    failures += int(r.attrib.get("failures",0))
    errors += int(r.attrib.get("errors",0))
    skipped += int(r.attrib.get("skipped",0))
    for tc in r.iter("testcase"):
        if tc.attrib.get("classname"): classes.add(tc.attrib["classname"].split(".")[-1])
assert failures==0 and errors==0, (failures,errors)
assert "CoreGradleTestBridge" in classes, classes
required={
    "CoreGradleTestBridge",
    "SourceSufficiencyJUnitTest",
    "Phase7BuildingPlacementJUnitTest",
    "NativeSourceReconciliationTest",
    "OutlinedCommissioningContractTest",
}
missing=required-classes
assert not missing, missing
Path("evidence/territory-standard-qa.json").write_text(json.dumps({
    "schema":"territory-phase-c-standard-qa-v1",
    "fullCoreContractSmoke":True,
    "tests":tests,
    "failures":failures,
    "errors":errors,
    "skipped":skipped,
    "requiredSuites":sorted(required),
    "status":"PASS"
},indent=2)+"\n")
PY

(cd tcs-src && gradle --no-daemon --build-cache :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease) > evidence/build.log 2>&1 || { cat evidence/build.log; exit 1; }
cp tcs-src/app/build/reports/lint-results-release.xml evidence/lint-results-release.xml
cp tcs-src/app/build/outputs/apk/debug/app-debug.apk evidence/Territory-PhaseC-1.3.0-debug.apk
cp tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk evidence/Territory-PhaseC-1.3.0-androidTest.apk
cp tcs-src/app/build/outputs/apk/release/app-release.apk evidence/Territory-Android-1.3.0-PhaseC-private.apk
cp tcs-src/app/build/outputs/bundle/release/app-release.aab evidence/Territory-Android-1.3.0-PhaseC-private.aab
sha256sum evidence/*.apk evidence/*.aab > evidence/builds.sha256

AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
"$AAPT2" dump badging evidence/Territory-Android-1.3.0-PhaseC-private.apk > evidence/badging.txt
grep -q "versionCode='49'" evidence/badging.txt
grep -q "versionName='1.3.0'" evidence/badging.txt
grep -q "application-label:'Territory'" evidence/badging.txt
"$AAPT2" dump permissions evidence/Territory-Android-1.3.0-PhaseC-private.apk > evidence/permissions.txt
grep -q "uses-permission: name='android.permission.INTERNET'" evidence/permissions.txt
"$APKSIGNER" verify --verbose --print-certs evidence/Territory-Android-1.3.0-PhaseC-private.apk > evidence/apk-signature.txt
unzip -t evidence/Territory-Android-1.3.0-PhaseC-private.apk > evidence/apk-ziptest.txt
unzip -t evidence/Territory-Android-1.3.0-PhaseC-private.aab > evidence/aab-ziptest.txt
