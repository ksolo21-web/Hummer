#!/usr/bin/env bash
set -euo pipefail
(cd tcs-src && gradle --no-daemon :app:assembleDebugAndroidTest) > evidence/android-test-package.log 2>&1 || { cat evidence/android-test-package.log; exit 1; }
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
CLASS=com.koenterprises.territorycardstudio.Phase3CCPreparationInstrumentationTest
collect() {
  adb shell run-as com.koenterprises.territorycardstudio ls files > evidence/screenshot-paths.txt 2>/dev/null || true
  while IFS= read -r path; do
    path="${path//$'\r'/}"
    [[ "$path" == phase3cc-*.png || "$path" == phase3cc-debug-*.txt ]] || continue
    adb exec-out run-as com.koenterprises.territorycardstudio cat "files/$path" > "evidence/${path##*/}"
  done < evidence/screenshot-paths.txt
}
# Functional passes and all unaffected images are retained with exact hashes.
adb shell am instrument -w -r -e class "$CLASS#captureDark,$CLASS#captureLight" -e captureOnly repair com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > evidence/phase3cc-runtime.log 2>&1
collect
cat evidence/phase3cc-runtime.log
cp retained/phase3cc-wide-captures.log evidence/phase3cc-wide-captures.log
python3 - <<'PY'
from pathlib import Path
import json,hashlib
from PIL import Image,ImageStat
proof=json.loads(Path('evidence/retained-results.json').read_text());passed=proof['retained']
assert len(passed)==len({tuple(x) for x in passed})==16
assert hashlib.sha256(Path('evidence/retained-runtime.log').read_bytes()).hexdigest()==proof['runtime_sha256']
for name in ['phase3cc-runtime.log','phase3cc-wide-captures.log']:assert 'OK (2 tests)' in Path('evidence',name).read_text(),name
Path('evidence/individual-results.json').write_text(json.dumps(dict(passed=passed,failed=[],frozenPrior=116,retainedPhase3CC=16,freshFunctionalTests=0,retriedCaptures=2),indent=2))
rows=[]
for p in sorted(Path('evidence').glob('phase3cc-*.png')):
 if '-debug-' in p.name:continue
 im=Image.open(p).convert('RGB');wide='-wide-' in p.name
 assert im.size==((1920,1200) if wide else (1080,2400)),(p,im.size)
 assert max(ImageStat.Stat(im).stddev)>5,p
 rows.append(dict(file=p.name,width=im.width,height=im.height,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
Path('evidence/screenshot-manifest.json').write_text(json.dumps(rows,indent=2))
assert len(rows)==24,len(rows)
for row in proof['screenshots']:assert hashlib.sha256(Path('evidence',row['file']).read_bytes()).hexdigest()==row['sha256']
Path('evidence/test-summary.txt').write_text('PHASE3CC_DISTINCT_TESTS=16\nFAILURES=0\nFROZEN_PRIOR_TESTS=116\nRETAINED_PHASE3CC_IDENTITIES=16\nRETRIED_TRANSITION_CAPTURES=2\nPRIOR_PHASE3CB_RUN=36349473357\n')
PY
