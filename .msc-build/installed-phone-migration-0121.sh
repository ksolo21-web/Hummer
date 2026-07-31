#!/usr/bin/env bash
set -euo pipefail

EVIDENCE="installed-0121-evidence/migration"
mkdir -p "$EVIDENCE"
BASELINE_APK="dist/MyStudyCompanion-phone-0.12.0-migration-baseline-debug.apk"
NEW_APK="dist/MyStudyCompanion-phone-0.12.1-debug.apk"
PACKAGE="com.mystudycompanion.app.debug"
test -f "$BASELINE_APK"
test -f "$NEW_APK"
unzip -tq "$BASELINE_APK"
unzip -tq "$NEW_APK"

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

launch_package() {
  local component
  component="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PACKAGE" | tr -d '\r' | tail -n 1)"
  printf '%s\n' "$component" | tee "$EVIDENCE/phone-component.txt"
  echo "$component" | grep -q "$PACKAGE"
  adb shell am force-stop "$PACKAGE"
  adb shell am start -n "$component" | tee "$EVIDENCE/phone-launch.txt"
  grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/phone-launch.txt"
  for attempt in $(seq 1 60); do
    adb shell dumpsys activity activities > /tmp/phone-activity.txt
    if grep -E "mResumedActivity=.*${PACKAGE}/|Resumed: ActivityRecord.*${PACKAGE}/|topResumedActivity=.*${PACKAGE}/" /tmp/phone-activity.txt >/dev/null; then
      sleep 3
      return 0
    fi
    sleep 2
  done
  cp /tmp/phone-activity.txt "$EVIDENCE/phone-activity-timeout.txt"
  return 1
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
install_apk "$BASELINE_APK" "$PACKAGE" baseline-0120
launch_package
if python3 /tmp/msc-ui.py exists 'Enter owner-only private alpha'; then
  python3 /tmp/msc-ui.py tap 'Enter owner-only private alpha'
fi
for attempt in $(seq 1 45); do
  if adb shell run-as "$PACKAGE" test -f databases/my-study-companion.db >/dev/null 2>&1; then break; fi
  [[ "$attempt" != 45 ]] || { echo 'Baseline Room database was not created.' >&2; exit 1; }
  sleep 2
done
adb shell am force-stop "$PACKAGE"
adb exec-out run-as "$PACKAGE" cat databases/my-study-companion.db > "$EVIDENCE/database-v6.db"
python3 - <<'PY'
import sqlite3
from pathlib import Path
path = Path('installed-0121-evidence/migration/database-v6.db')
assert path.stat().st_size > 0
version = sqlite3.connect(f'file:{path}?mode=ro', uri=True).execute('PRAGMA user_version').fetchone()[0]
print(f'Baseline Room user_version={version}')
if version != 6:
    raise SystemExit(f'Expected baseline database version 6, got {version}.')
PY

install_apk "$NEW_APK" "$PACKAGE" upgrade-0121
adb shell dumpsys package "$PACKAGE" > "$EVIDENCE/upgraded-package.txt"
grep -q 'versionCode=25' "$EVIDENCE/upgraded-package.txt"
grep -q 'versionName=0.12.1-private-alpha-grounded-links-debug' "$EVIDENCE/upgraded-package.txt"
launch_package
if python3 /tmp/msc-ui.py exists 'Enter owner-only private alpha'; then
  python3 /tmp/msc-ui.py tap 'Enter owner-only private alpha'
fi
python3 /tmp/msc-ui.py tap 'More'
python3 /tmp/msc-ui.py assert 'AI Study Assistant'
python3 /tmp/msc-ui.py tap 'AI Study Assistant'
python3 /tmp/msc-ui.py assert 'Study Assistant'
python3 /tmp/msc-ui.py assert 'Offline Study AI'
adb exec-out screencap -p > "$EVIDENCE/phone-ai-screen.png"

adb shell wm size 1600x2560
adb shell wm density 320
launch_package
python3 /tmp/msc-ui.py tap 'More'
python3 /tmp/msc-ui.py tap 'AI Study Assistant'
python3 /tmp/msc-ui.py assert 'Source protection'
python3 /tmp/msc-ui.py assert 'only the verified sources the answer actually used'
adb exec-out screencap -p > "$EVIDENCE/tablet-ai-source-protection.png"
adb shell wm size reset
adb shell wm density reset

adb shell am force-stop "$PACKAGE"
adb exec-out run-as "$PACKAGE" cat databases/my-study-companion.db > "$EVIDENCE/database-v7.db"
python3 - <<'PY'
import sqlite3
from pathlib import Path
path = Path('installed-0121-evidence/migration/database-v7.db')
assert path.stat().st_size > 0
connection = sqlite3.connect(f'file:{path}?mode=ro', uri=True)
version = connection.execute('PRAGMA user_version').fetchone()[0]
tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
print(f'Upgraded Room user_version={version}; tables={len(tables)}')
if version != 7:
    raise SystemExit(f'Expected upgraded database version 7, got {version}.')
if not tables:
    raise SystemExit('Upgraded database has no tables.')
PY

adb logcat -d > "$EVIDENCE/logcat.txt"
python3 - <<'PY'
from pathlib import Path
lines = Path('installed-0121-evidence/migration/logcat.txt').read_text(errors='replace').splitlines()
package = 'com.mystudycompanion.app.debug'
for index, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line:
        block = '\n'.join(lines[index:index + 100])
        if f'Process: {package}' in block:
            raise SystemExit('Phone app produced a package-specific fatal Android exception.')
PY
printf '%s\n' 'PASS: paired 0.12.0 source-stage APK created a Room v6 database; paired 0.12.1 APK upgraded it in place to v7, launched on phone and tablet geometry, displayed the hardened AI source-protection UI, and produced no package-specific fatal exception.' | tee "$EVIDENCE/RESULT.txt"
