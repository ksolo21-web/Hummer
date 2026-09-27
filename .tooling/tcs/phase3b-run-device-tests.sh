#!/usr/bin/env bash
set -euo pipefail
(cd tcs-src && gradle --no-daemon :app:assembleDebugAndroidTest) > evidence/android-test-package.log 2>&1
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
CLASS=com.koenterprises.territorycardstudio.Phase3BEditorUiInstrumentationTest
collect() {
  adb shell run-as com.koenterprises.territorycardstudio sh -c 'ls files/phase3b-*.png' > evidence/screenshot-paths.txt 2>/dev/null || true
  while IFS= read -r path; do
    path="${path//$'\r'/}"
    [[ "$path" == files/phase3b-*.png ]] || continue
    adb exec-out run-as com.koenterprises.territorycardstudio cat "$path" > "evidence/${path##*/}"
  done < evidence/screenshot-paths.txt
}
adb shell am instrument -w -r -e class "$CLASS" com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > evidence/phase3b-runtime.log 2>&1
collect
cat evidence/phase3b-runtime.log
python3 - <<'PY'
from pathlib import Path
import json,xml.etree.ElementTree as E
text=Path('evidence/phase3b-runtime.log').read_text()
passed=[];failed=[];cls=None;name=None
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
 if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
 if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
  code=int(line.rsplit(' ',1)[1])
  if code<=0 and name:
   (passed if code==0 else failed).append((cls,name));cls=None;name=None
Path('evidence/individual-results.json').write_text(json.dumps(dict(passed=passed,failed=failed,frozenPrior=77),indent=2))
root=E.Element('testsuite',name='Phase3B editor',tests=str(len(passed)+len(failed)),failures=str(len(failed)),errors='0')
for c,n in passed+failed:
 row=E.SubElement(root,'testcase',classname=c,name=n)
 if (c,n) in failed:E.SubElement(row,'failure').text='See phase3b-runtime.log'
E.ElementTree(root).write('evidence/TEST-phase3b.xml',encoding='UTF-8',xml_declaration=True)
assert len(passed)==len(set(passed))==10 and not failed and 'OK (10 tests)' in text,(passed,failed)
Path('evidence/test-summary.txt').write_text('NEW_PHASE3B_TESTS=10\nFAILURES=0\nFROZEN_PRIOR_TESTS=77\nPRIOR_PHASE3A_RUN=36334431100\n')
PY
adb shell wm size 1920x1200
adb shell wm density 160
adb shell am force-stop com.koenterprises.territorycardstudio
adb shell am instrument -w -r -e class "$CLASS#captureLight,$CLASS#captureDark" com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > evidence/phase3b-wide-captures.log 2>&1
collect
cat evidence/phase3b-wide-captures.log
grep -F 'OK (2 tests)' evidence/phase3b-wide-captures.log
python3 - <<'PY'
from PIL import Image,ImageStat
from pathlib import Path
import json,hashlib
files=sorted(Path('evidence').glob('phase3b-*.png'))
assert len(files)==14,[p.name for p in files]
rows=[]
for p in files:
 im=Image.open(p).convert('RGB');wide='-wide-' in p.name
 assert im.size==((1920,1200) if wide else (1080,2400)),(p,im.size)
 assert max(ImageStat.Stat(im).stddev)>5,p
 rows.append(dict(file=p.name,width=im.width,height=im.height,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
Path('evidence/screenshot-manifest.json').write_text(json.dumps(rows,indent=2))
PY
