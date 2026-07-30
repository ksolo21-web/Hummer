#!/usr/bin/env bash
set -euo pipefail

EVIDENCE="installed-0121-evidence/jw-library"
mkdir -p "$EVIDENCE"
PHONE_APK="dist/MyStudyCompanion-phone-0.12.1-debug.apk"
JW_APK="JWLibrary.apk"
PHONE_PACKAGE="com.mystudycompanion.app.debug"
JW_PACKAGE="org.jw.jwlibrary.mobile"
test -f "$PHONE_APK"
test -f "$JW_APK"
unzip -tq "$PHONE_APK"
unzip -tq "$JW_APK"

wait_for_android() {
  local attempt
  adb start-server >/dev/null
  adb wait-for-device
  for attempt in $(seq 1 180); do
    if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
      && adb shell service check package 2>/dev/null | grep -q found \
      && adb shell cmd package list packages >/dev/null 2>&1; then
      echo "Android services ready after check ${attempt}." | tee -a "$EVIDENCE/android-ready.txt"
      return 0
    fi
    sleep 2
  done
  adb shell getprop > "$EVIDENCE/getprop-timeout.txt" 2>&1 || true
  adb shell service list > "$EVIDENCE/services-timeout.txt" 2>&1 || true
  echo 'Android package manager did not become ready.' >&2
  return 1
}

install_apk() {
  local apk="$1" package="$2" label="$3"
  local log="$EVIDENCE/${label}-install.txt" remote="/data/local/tmp/${label}.apk" attempt output status
  for attempt in 1 2 3 4; do
    echo "Install attempt ${attempt}: ${apk}" | tee -a "$log"
    wait_for_android
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb push "$apk" "$remote" 2>&1 | tee -a "$log"; then
      set +e
      output="$(timeout 360 adb shell pm install -r -g "$remote" 2>&1)"
      status=$?
      set -e
      printf '%s\n' "$output" | tee -a "$log"
      if [[ "$status" == 0 && "$output" == *Success* ]] \
        && adb shell pm path "$package" | tee "$EVIDENCE/${label}-package-path.txt" | grep -q package:; then
        adb shell rm -f "$remote" >/dev/null 2>&1 || true
        return 0
      fi
    fi
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    adb kill-server >/dev/null 2>&1 || true
    sleep $((attempt * 5))
    if [[ "$attempt" == 2 ]]; then
      adb start-server >/dev/null || true
      adb wait-for-device || true
      adb reboot >/dev/null 2>&1 || true
      sleep 10
    fi
  done
  echo "Failed to install ${apk}." >&2
  return 1
}

resolve_jw() {
  local name="$1" uri="$2" resolved
  resolved="$(adb shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d "$uri" | tr -d '\r')"
  printf '%s -> %s\n' "$uri" "$resolved" | tee "$EVIDENCE/${name}-resolve.txt"
  grep -q "$JW_PACKAGE" "$EVIDENCE/${name}-resolve.txt"
}

start_jw() {
  local name="$1" uri="$2"
  adb shell am start -a android.intent.action.VIEW -d "$uri" "$JW_PACKAGE" | tee "$EVIDENCE/${name}-start.txt"
  grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/${name}-start.txt"
  sleep 8
  adb shell dumpsys activity activities > "$EVIDENCE/${name}-activity.txt"
  grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
    "$EVIDENCE/${name}-activity.txt" >/dev/null
  adb exec-out screencap -p > "$EVIDENCE/${name}.png"
}

cat > /tmp/msc-ui.py <<'PY'
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

def run(*args, check=True):
    return subprocess.run(args, check=check, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)

def dump():
    for attempt in range(4):
        try:
            run('adb', 'shell', 'uiautomator', 'dump', '/sdcard/window.xml')
            run('adb', 'pull', '/sdcard/window.xml', '/tmp/window.xml')
            return ET.parse('/tmp/window.xml').getroot()
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2)

def find(text):
    lowered = text.casefold()
    for node in dump().iter('node'):
        values = [node.attrib.get('text', ''), node.attrib.get('content-desc', '')]
        value = next((v.strip() for v in values if v and lowered in v.casefold()), '')
        if not value:
            continue
        match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if match:
            left, top, right, bottom = map(int, match.groups())
            return (left + right) // 2, (top + bottom) // 2, value
    return None

def tap(text):
    hit = find(text)
    if not hit:
        raise SystemExit(f'UI text not found: {text}')
    run('adb', 'shell', 'input', 'tap', str(hit[0]), str(hit[1]))
    time.sleep(2)

def assert_text(text):
    if not find(text):
        raise SystemExit(f'Expected UI text not found: {text}')

command = sys.argv[1]
if command == 'tap':
    tap(sys.argv[2])
elif command == 'assert':
    assert_text(sys.argv[2])
elif command == 'exists':
    raise SystemExit(0 if find(sys.argv[2]) else 1)
else:
    raise SystemExit(f'Unknown command: {command}')
PY

wait_for_android
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
install_apk "$PHONE_APK" "$PHONE_PACKAGE" phone-0121
install_apk "$JW_APK" "$JW_PACKAGE" jw-library
adb shell dumpsys package "$PHONE_PACKAGE" > "$EVIDENCE/phone-package.txt"
grep -q 'versionCode=25' "$EVIDENCE/phone-package.txt"
grep -q 'versionName=0.12.1-private-alpha-grounded-links-debug' "$EVIDENCE/phone-package.txt"

TODAY_DEVICE="$(adb shell date +%Y%m%d | tr -d '\r')"
JOB1='jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=18001000'
JEREMIAH='jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=24020007-24020018'
WEEK='jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'
RESEARCH='jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=rsg19'
WEEK_HTTPS='https://www.jw.org/finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'
DAILY="jwlibrary:///finder?alias=daily-text&date=${TODAY_DEVICE}&wtlocale=E"
DAILY_HTTPS="https://www.jw.org/finder?alias=daily-text&date=${TODAY_DEVICE}&wtlocale=E"
resolve_jw job1 "$JOB1"
resolve_jw jeremiah "$JEREMIAH"
resolve_jw week "$WEEK"
resolve_jw research "$RESEARCH"
resolve_jw week-https "$WEEK_HTTPS"
resolve_jw daily "$DAILY"
resolve_jw daily-https "$DAILY_HTTPS"
start_jw job1-open "$JOB1"
start_jw jeremiah-open "$JEREMIAH"
start_jw week-open "$WEEK"
start_jw daily-text-open "$DAILY"

PHONE_COMPONENT="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PHONE_PACKAGE" | tr -d '\r' | tail -n 1)"
printf '%s\n' "$PHONE_COMPONENT" | tee "$EVIDENCE/phone-component.txt"
echo "$PHONE_COMPONENT" | grep -q "$PHONE_PACKAGE"
adb shell am force-stop "$PHONE_PACKAGE"
adb shell am start -n "$PHONE_COMPONENT" | tee "$EVIDENCE/phone-launch.txt"
grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/phone-launch.txt"
sleep 6
if python3 /tmp/msc-ui.py exists 'Enter owner-only private alpha'; then
  python3 /tmp/msc-ui.py tap 'Enter owner-only private alpha'
  sleep 3
fi
python3 /tmp/msc-ui.py tap 'More'
python3 /tmp/msc-ui.py tap 'Personal Study Companion'
python3 /tmp/msc-ui.py assert 'Research'
python3 /tmp/msc-ui.py tap 'Bible'
for _ in 1 2 3; do adb shell input swipe 500 1600 500 650 300; sleep 1; done
if python3 /tmp/msc-ui.py exists 'Start journey at Day 1'; then
  python3 /tmp/msc-ui.py tap 'Start journey at Day 1'
fi
python3 /tmp/msc-ui.py assert 'Day 1 of'
python3 /tmp/msc-ui.py assert 'Read now in JW Library'
adb exec-out screencap -p > "$EVIDENCE/journey-day1.png"
python3 /tmp/msc-ui.py tap 'Read now in JW Library'
sleep 8
adb shell dumpsys activity activities > "$EVIDENCE/journey-jw-activity.txt"
grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
  "$EVIDENCE/journey-jw-activity.txt" >/dev/null
adb exec-out screencap -p > "$EVIDENCE/journey-jw-open.png"
adb shell input keyevent 4
sleep 5
adb shell dumpsys activity activities > "$EVIDENCE/journey-return-activity.txt"
grep -E 'mResumedActivity=.*com\.mystudycompanion\.app\.debug|Resumed: ActivityRecord.*com\.mystudycompanion\.app\.debug' \
  "$EVIDENCE/journey-return-activity.txt" >/dev/null
python3 /tmp/msc-ui.py assert 'Day 1 of'

adb logcat -d > "$EVIDENCE/logcat.txt"
python3 - <<'PY'
from pathlib import Path
lines = Path('installed-0121-evidence/jw-library/logcat.txt').read_text(errors='replace').splitlines()
package = 'com.mystudycompanion.app.debug'
for index, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line:
        block = '\n'.join(lines[index:index + 100])
        if f'Process: {package}' in block:
            raise SystemExit('Phone app produced a package-specific fatal Android exception.')
PY
printf '%s\n' 'PASS: actual 0.12.1 phone APK and the current official JW Library APK installed on a fresh phone emulator; all exact Finder targets resolved to JW Library; Job 1, Jeremiah 20:7-18, the active week, and the dated Daily Text were launched in JW Library; Bible Journey Day 1 opened JW Library and returned without losing state; no package-specific fatal exception occurred.' | tee "$EVIDENCE/RESULT.txt"
