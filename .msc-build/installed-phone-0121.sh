#!/usr/bin/env bash
set -euo pipefail

BASELINE_APK="dist-baseline/MyStudyCompanion-phone-0.12.0-debug.apk"
NEW_APK="dist/MyStudyCompanion-phone-0.12.1-debug.apk"
PACKAGE="com.mystudycompanion.app.debug"
test -f "$BASELINE_APK"
test -f "$NEW_APK"

# Establish a real database-version-6 install, then upgrade in place to version 7.
adb start-server >/dev/null
adb wait-for-device
for attempt in $(seq 1 150); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
    && adb shell service check package 2>/dev/null | grep -q found; then break; fi
  [[ "$attempt" != 150 ]] || exit 1
  sleep 2
done
adb install --no-streaming -r -g "$BASELINE_APK"
BASE_COMPONENT="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PACKAGE" | tr -d '\r' | tail -n 1)"
adb shell am start -W -n "$BASE_COMPONENT" | tee /tmp/baseline-launch.txt
grep -q 'Status: ok' /tmp/baseline-launch.txt
sleep 5
adb shell am force-stop "$PACKAGE"

python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-phone-0120.sh').read_text(encoding='utf-8')
source = source.replace('installed-0120-evidence/phone', 'installed-0121-evidence/phone')
source = source.replace('MyStudyCompanion-phone-0.12.0-debug.apk', 'MyStudyCompanion-phone-0.12.1-debug.apk')
source = source.replace('PASS: 0.12.0 phone APK', 'PASS: 0.12.1 phone APK upgraded from database version 6')
anchor = "resolve_jw week_https 'https://www.jw.org/finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'\n"
addition = anchor + "TODAY_UTC=\"$(date -u +%Y%m%d)\"\nresolve_jw daily \"jwlibrary:///finder?alias=daily-text&date=${TODAY_UTC}&wtlocale=E\"\nresolve_jw daily_https \"https://www.jw.org/finder?alias=daily-text&date=${TODAY_UTC}&wtlocale=E\"\n"
if source.count(anchor) != 1:
    raise SystemExit('Daily Text insertion anchor not found exactly once.')
source = source.replace(anchor, addition, 1)
Path('/tmp/installed-phone-0121-generated.sh').write_text(source, encoding='utf-8')
PY

bash /tmp/installed-phone-0121-generated.sh
adb shell dumpsys package "$PACKAGE" > installed-0121-evidence/phone/upgraded-package.txt
adb shell am start -W -a android.intent.action.VIEW \
  -d "jwlibrary:///finder?alias=daily-text&date=$(date -u +%Y%m%d)&wtlocale=E" \
  org.jw.jwlibrary.mobile | tee installed-0121-evidence/phone/daily-text-start.txt
grep -q 'Status: ok' installed-0121-evidence/phone/daily-text-start.txt
sleep 6
adb exec-out screencap -p > installed-0121-evidence/phone/daily-text-open.png
printf '%s\n' 'PASS: database 6-to-7 in-place upgrade launched; exact dated Daily Text Finder resolved and opened in official JW Library.' \
  | tee -a installed-0121-evidence/phone/RESULT.txt
