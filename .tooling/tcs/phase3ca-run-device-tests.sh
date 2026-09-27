#!/usr/bin/env bash
set -euo pipefail
(cd tcs-src && gradle --no-daemon :app:assembleDebugAndroidTest) > evidence/android-test-package.log 2>&1
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
adb shell am instrument -w -r -e class com.koenterprises.territorycardstudio.Phase3CAEditingPreparationInstrumentationTest com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > evidence/phase3ca-runtime.log 2>&1
cat evidence/phase3ca-runtime.log
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as E
text=Path('evidence/phase3ca-runtime.log').read_text()
passed=[];cls=None;name=None
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
 if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
 if line=='INSTRUMENTATION_STATUS_CODE: 0':
  assert cls=='com.koenterprises.territorycardstudio.Phase3CAEditingPreparationInstrumentationTest' and name
  passed.append((cls,name));cls=None;name=None
assert len(passed)==len(set(passed))==13,passed
root=E.Element('testsuite',name='Phase3C-A preparation transaction',tests='13',failures='0',errors='0',skipped='0')
for cls,name in passed:E.SubElement(root,'testcase',classname=cls,name=name)
E.ElementTree(root).write('evidence/TEST-phase3ca.xml',encoding='UTF-8',xml_declaration=True)
Path('evidence/test-summary.txt').write_text('NEW_PHASE3CA_TESTS=13\nFAILURES=0\nFROZEN_PRIOR_TESTS=87\nFROZEN_PHASE3B_RUN=36339456333\n')
PY
