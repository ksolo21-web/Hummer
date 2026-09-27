#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/android-test-results
trap 'rc=$?; if [ "$rc" -ne 0 ]; then adb exec-out screencap -p > evidence/runtime-failure-screen.png 2>/dev/null || true; adb shell dumpsys window > evidence/runtime-failure-window.txt 2>/dev/null || true; adb shell dumpsys power > evidence/runtime-failure-power.txt 2>/dev/null || true; fi' EXIT
frozen=.tooling/tcs/phase2k-evidence/frozen-run36323755251-pass62.xml
echo '61197a5f9e7ac1eeaca85fc88d00f89ce386b6c365794d908a23245b73fbcd64  .tooling/tcs/phase2k-evidence/frozen-run36323755251-pass62.xml' | sha256sum -c -
cp "$frozen" evidence/android-test-results/TEST-frozen-pass62.xml
rm -rf tcs-src/app/build/outputs/androidTest-results/connected/debug
selector='com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest#captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes'
set +e
(cd tcs-src && gradle --no-daemon :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selector") > evidence/connected-android-test.log 2>&1
retry_rc=$?
set -e
xml="$(find tcs-src/app/build/outputs/androidTest-results/connected/debug -name 'TEST*.xml' -type f | head -n 1)"
test -n "$xml"
cp "$xml" evidence/android-test-results/TEST-retried-phase2k-capture.xml
if [ "$retry_rc" -ne 0 ]; then exit "$retry_rc"; fi
python3 - <<'PY'
import xml.etree.ElementTree as E
from pathlib import Path
cases=[c for p in Path('evidence/android-test-results').glob('TEST*.xml') for c in E.parse(p).getroot().iter('testcase')]
assert len(cases)==63, len(cases)
ids={(c.get('classname'),c.get('name')) for c in cases}
assert len(ids)==63
assert ('com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest','captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes') in ids
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
PY
grep -F 'BUILD SUCCESSFUL' evidence/connected-android-test.log >/dev/null
printf 'PHASE2K_CONNECTED_TESTS=PASS_63\nFROZEN_PASSING_CASES=62\nFROZEN_SOURCE_RUN=36323755251\nRETRIED_FAILED_CASES=1\n' > evidence/connected-test-status.txt
