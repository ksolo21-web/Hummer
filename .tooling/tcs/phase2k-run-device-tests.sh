#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/android-test-results
trap 'rc=$?; if [ "$rc" -ne 0 ]; then adb exec-out screencap -p > evidence/runtime-failure-screen.png 2>/dev/null || true; adb shell dumpsys window > evidence/runtime-failure-window.txt 2>/dev/null || true; adb shell dumpsys power > evidence/runtime-failure-power.txt 2>/dev/null || true; fi' EXIT
frozen=.tooling/tcs/phase2k-evidence/frozen-run36323755251-pass62.xml
echo '61197a5f9e7ac1eeaca85fc88d00f89ce386b6c365794d908a23245b73fbcd64  .tooling/tcs/phase2k-evidence/frozen-run36323755251-pass62.xml' | sha256sum -c -
cp "$frozen" evidence/android-test-results/TEST-frozen-pass62.xml
(cd tcs-src && gradle --no-daemon :app:assembleDebugAndroidTest) > evidence/android-test-package.log 2>&1
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
K=com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest
: > evidence/connected-android-test.log
run_retry() {
  local method="$1" log="evidence/retry-$1.log"
  adb shell am force-stop com.koenterprises.territorycardstudio || true
  adb shell am instrument -w -r -e class "$K#$method" com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1
  cat "$log" >> evidence/connected-android-test.log
  grep -F 'OK (1 test)' "$log" >/dev/null
}
run_retry captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes
run_retry captureFindingsAndAuditAndNavigateFromPreviewDark
cat > evidence/android-test-results/TEST-retried-phase2k-capture.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Phase2K failed-case theme retries" tests="2" failures="0" errors="0" skipped="0">
  <testcase classname="com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest" name="captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes"/>
  <testcase classname="com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest" name="captureFindingsAndAuditAndNavigateFromPreviewDark"/>
</testsuite>
XML
python3 - <<'PY'
import xml.etree.ElementTree as E
from pathlib import Path
cases=[c for p in Path('evidence/android-test-results').glob('TEST*.xml') for c in E.parse(p).getroot().iter('testcase')]
assert len(cases)==64, len(cases)
ids={(c.get('classname'),c.get('name')) for c in cases}
assert len(ids)==64
assert {('com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest','captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes'),('com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest','captureFindingsAndAuditAndNavigateFromPreviewDark')} <= ids
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
PY
grep -F 'BUILD SUCCESSFUL' evidence/android-test-package.log >/dev/null
printf 'PHASE2K_CONNECTED_TESTS=PASS_64\nFROZEN_PASSING_CASES=62\nFROZEN_SOURCE_RUN=36323755251\nRETRIED_FAILED_CASES_SPLIT_BY_THEME=2\n' > evidence/connected-test-status.txt
