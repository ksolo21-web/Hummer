#!/usr/bin/env bash
set -euo pipefail
adb install -r evidence/TerritoryCardStudio-Android-0.6.1.apk > evidence/app-install.log
adb install -r evidence/emulator-test.apk > evidence/test-install.log
PKG=com.koenterprises.territorycardstudio
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -b crash -c
timeout 600 adb shell am instrument -w -r -e class "$PKG.OutlinedImageInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/outlined-image.log 2>&1 || true
timeout 600 adb shell am instrument -w -r -e class "$PKG.Phase6ImageInterpretationInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/outlined-colored-regression.log 2>&1 || true
timeout 600 adb shell am instrument -w -r -e class "$PKG.OutlinedGroupReviewInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/outlined-group-review.log 2>&1 || true
timeout 600 adb shell am instrument -w -r -e class "$PKG.SupplementalReferenceInstrumentationTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > evidence/supplemental-reference.log 2>&1 || true
adb logcat -b crash -d > evidence/outlined-image-crash.log
adb shell run-as "$PKG" ls files > evidence/outlined-capture-paths.txt
while IFS= read -r path; do
 path="${path//$'\r'/}"
 [[ "$path" == outlined-* ]] || continue
 adb exec-out run-as "$PKG" cat "files/$path" > "evidence/${path##*/}"
done < evidence/outlined-capture-paths.txt
python3 - <<'PY'
from pathlib import Path
import json
supplemental=Path('evidence/supplemental-reference.log').read_text()
assert 'OK (2 tests)' in supplemental,supplemental
s=Path('evidence/outlined-image.log').read_text()
assert 'OK (2 tests)' in s,s
grouped=Path('evidence/outlined-group-review.log').read_text()
assert 'OK (2 tests)' in grouped,grouped
assert Path('evidence/outlined-group-a265-selected.png').stat().st_size>1000
colored=Path('evidence/outlined-colored-regression.log').read_text()
assert 'OK (1 test)' in colored,colored
d=json.loads(Path('evidence/outlined-actual-source-analysis.json').read_text())
assert d['sourceSha256']=='a93294659bd8ec40937fde5b20a8e0f89315d20c69ebe25e92318fb5665bbec3'
assert d['roads'] and all(r['status']=='context' for r in d['roads'])
PY
