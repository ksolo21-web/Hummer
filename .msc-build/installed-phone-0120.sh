#!/usr/bin/env bash
set -euo pipefail

EVIDENCE="installed-0120-evidence/phone"
mkdir -p "$EVIDENCE"
PHONE_APK="dist/MyStudyCompanion-phone-0.12.0-debug.apk"
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
  for attempt in $(seq 1 150); do
    if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
      && adb shell service check package 2>/dev/null | grep -q found \
      && adb shell cmd package list packages >/dev/null 2>&1; then
      echo "Android services ready after check ${attempt}." | tee "$EVIDENCE/android-ready.txt"
      return 0
    fi
    sleep 2
  done
  adb shell getprop > "$EVIDENCE/getprop-timeout.txt" 2>&1 || true
  adb shell service list > "$EVIDENCE/services-timeout.txt" 2>&1 || true
  echo "Android package manager did not become ready." >&2
  return 1
}

install_apk() {
  local apk="$1" package="$2" label="$3" log="$EVIDENCE/${label}-install.txt"
  local attempt remote
  remote="/data/local/tmp/${label}.apk"
  for attempt in 1 2 3; do
    echo "Install attempt ${attempt}: ${apk}" | tee -a "$log"
    if adb install --no-streaming -r -g "$apk" 2>&1 | tee -a "$log" | grep -q Success; then
      adb shell pm path "$package" | tee "$EVIDENCE/${label}-package-path.txt"
      return 0
    fi
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb push "$apk" "$remote" 2>&1 | tee -a "$log" \
      && adb shell pm install -r -g "$remote" 2>&1 | tee -a "$log" | grep -q Success; then
      adb shell rm -f "$remote" >/dev/null 2>&1 || true
      adb shell pm path "$package" | tee "$EVIDENCE/${label}-package-path.txt"
      return 0
    fi
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    sleep $((attempt * 4))
  done
  echo "Failed to install ${apk}." >&2
  return 1
}

wait_for_android
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
install_apk "$JW_APK" "$JW_PACKAGE" jw-library
install_apk "$PHONE_APK" "$PHONE_PACKAGE" phone

test -n "$(adb shell pm path "$JW_PACKAGE" | tr -d '\r')"
test -n "$(adb shell pm path "$PHONE_PACKAGE" | tr -d '\r')"

resolve_jw() {
  local name="$1" uri="$2" resolved
  resolved="$(adb shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d "$uri" | tr -d '\r')"
  printf '%s -> %s\n' "$uri" "$resolved" | tee "$EVIDENCE/${name}-resolve.txt"
  grep -q "$JW_PACKAGE" "$EVIDENCE/${name}-resolve.txt"
}

resolve_jw job1 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=18001000'
resolve_jw jeremiah 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=24020007-24020018'
resolve_jw week 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'
resolve_jw research 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=rsg19'
resolve_jw week_https 'https://www.jw.org/finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'

adb shell am start -W -a android.intent.action.VIEW \
  -d 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=18001000' \
  "$JW_PACKAGE" | tee "$EVIDENCE/jw-start.txt"
grep -q 'Status: ok' "$EVIDENCE/jw-start.txt"
sleep 8
adb shell dumpsys activity activities > "$EVIDENCE/jw-activity.txt"
grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
  "$EVIDENCE/jw-activity.txt" >/dev/null
adb exec-out screencap -p > "$EVIDENCE/jw-job1.png"

cat > /tmp/msc-ui.py <<'PY'
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def run(*args, check=True):
    return subprocess.run(args, check=check, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=45)


def dump():
    for attempt in range(3):
        try:
            run('adb', 'shell', 'uiautomator', 'dump', '/sdcard/window.xml')
            run('adb', 'pull', '/sdcard/window.xml', '/tmp/window.xml')
            return ET.parse('/tmp/window.xml').getroot()
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2)


def find(text):
    root = dump()
    lowered = text.casefold()
    for node in root.iter('node'):
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


def exists(text):
    raise SystemExit(0 if find(text) else 1)


command = sys.argv[1]
if command == 'tap':
    tap(sys.argv[2])
elif command == 'assert':
    assert_text(sys.argv[2])
elif command == 'exists':
    exists(sys.argv[2])
else:
    raise SystemExit(f'Unknown command: {command}')
PY

PHONE_COMPONENT="$(adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PHONE_PACKAGE" \
  | tr -d '\r' | tail -n 1)"
printf '%s\n' "$PHONE_COMPONENT" | tee "$EVIDENCE/phone-launch-component.txt"
echo "$PHONE_COMPONENT" | grep -q "$PHONE_PACKAGE"

launch_companion() {
  adb shell am force-stop "$PHONE_PACKAGE"
  adb shell am start -W -n "$PHONE_COMPONENT" | tee "$EVIDENCE/phone-launch.txt"
  grep -q 'Status: ok' "$EVIDENCE/phone-launch.txt"
  sleep 5
  if python3 /tmp/msc-ui.py exists 'Enter owner-only private alpha'; then
    python3 /tmp/msc-ui.py tap 'Enter owner-only private alpha'
    sleep 3
  fi
}

adb logcat -c
launch_companion
python3 /tmp/msc-ui.py tap 'More'
python3 /tmp/msc-ui.py assert 'AI Study Assistant'
python3 /tmp/msc-ui.py tap 'AI Study Assistant'
python3 /tmp/msc-ui.py assert 'Study Assistant'
python3 /tmp/msc-ui.py assert 'AI system'
python3 /tmp/msc-ui.py assert 'Offline Study AI'
adb exec-out screencap -p > "$EVIDENCE/phone-ai-screen.png"

# Verify the expanded/tablet AI layout exposes the strengthened source boundary.
adb shell wm size 1600x2560
adb shell wm density 320
launch_companion
python3 /tmp/msc-ui.py tap 'More'
python3 /tmp/msc-ui.py tap 'AI Study Assistant'
python3 /tmp/msc-ui.py assert 'Source protection'
python3 /tmp/msc-ui.py assert 'only the verified sources the answer actually used'
adb exec-out screencap -p > "$EVIDENCE/tablet-ai-source-protection.png"

# Return to phone geometry and verify the Bible Journey exact-open/return path still works.
adb shell wm size reset
adb shell wm density reset
launch_companion
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
sleep 4
adb shell dumpsys activity activities > "$EVIDENCE/journey-return-activity.txt"
grep -E 'mResumedActivity=.*com\.mystudycompanion\.app\.debug|Resumed: ActivityRecord.*com\.mystudycompanion\.app\.debug' \
  "$EVIDENCE/journey-return-activity.txt" >/dev/null
python3 /tmp/msc-ui.py assert 'Day 1 of'

adb logcat -d > "$EVIDENCE/logcat.txt"
python3 - <<'PY'
from pathlib import Path
lines = Path('installed-0120-evidence/phone/logcat.txt').read_text(errors='replace').splitlines()
package = 'com.mystudycompanion.app.debug'
for index, line in enumerate(lines):
    if 'FATAL EXCEPTION' not in line:
        continue
    block = '\n'.join(lines[index:index + 100])
    if f'Process: {package}' in block:
        raise SystemExit('Phone app produced a package-specific fatal Android exception.')
PY

echo 'PASS: 0.12.0 phone APK installed and launched; AI phone/tablet layouts loaded; source-protection copy was present; official JW Library handled exact Finder targets; Bible Journey Day 1 opened JW Library and returned without losing state; no package-specific fatal exception occurred.' \
  | tee "$EVIDENCE/RESULT.txt"
