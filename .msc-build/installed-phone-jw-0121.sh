#!/usr/bin/env bash
set -euo pipefail

EVIDENCE='installed-0121-evidence/jw-library/bootstrap'
JW_APK='JWLibrary.apk'
JW_PACKAGE='org.jw.jwlibrary.mobile'
mkdir -p "$EVIDENCE"
test -f "$JW_APK"
unzip -tq "$JW_APK"

adb start-server >/dev/null
adb wait-for-device
for attempt in $(seq 1 180); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]] \
    && adb shell service check package 2>/dev/null | grep -q found \
    && adb shell cmd package list packages >/dev/null 2>&1; then
    break
  fi
  [[ "$attempt" != 180 ]] || { echo 'Android services were not ready for JW Library bootstrap.' >&2; exit 1; }
  sleep 2
done

# Install the current official APK once before the main verifier. The main
# verifier installs it again with -r, which preserves the initialized data.
REMOTE='/data/local/tmp/jw-library-bootstrap.apk'
adb shell rm -f "$REMOTE" >/dev/null 2>&1 || true
adb push "$JW_APK" "$REMOTE" | tee "$EVIDENCE/push.txt"
INSTALL_OUTPUT="$(timeout 360 adb shell pm install -r -g "$REMOTE" 2>&1)"
printf '%s\n' "$INSTALL_OUTPUT" | tee "$EVIDENCE/install.txt"
[[ "$INSTALL_OUTPUT" == *Success* ]]
adb shell rm -f "$REMOTE" >/dev/null 2>&1 || true
adb shell pm path "$JW_PACKAGE" | tee "$EVIDENCE/package-path.txt" | grep -q package:

JW_COMPONENT="$(adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$JW_PACKAGE" \
  | tr -d '\r' | tail -n 1)"
printf '%s\n' "$JW_COMPONENT" | tee "$EVIDENCE/component.txt"
echo "$JW_COMPONENT" | grep -q "$JW_PACKAGE"

adb shell am force-stop "$JW_PACKAGE"
adb logcat -c
adb shell am start -W -n "$JW_COMPONENT" | tee "$EVIDENCE/launch.txt"
grep -Eq 'Status: ok|Starting: Intent|Warning: Activity not started' "$EVIDENCE/launch.txt"

cat > /tmp/jw-bootstrap-ui.py <<'PY'
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

XML = '/tmp/jw-bootstrap.xml'

def run(*args, check=True):
    return subprocess.run(args, check=check, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True, timeout=60)

def dump():
    run('adb', 'shell', 'uiautomator', 'dump', '/sdcard/jw-bootstrap.xml')
    run('adb', 'pull', '/sdcard/jw-bootstrap.xml', XML)
    return ET.parse(XML).getroot()

def nodes(root):
    result = []
    for node in root.iter('node'):
        text = (node.attrib.get('text') or node.attrib.get('content-desc') or '').strip()
        match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if text and match:
            left, top, right, bottom = map(int, match.groups())
            result.append((text, (left + right)//2, (top + bottom)//2))
    return result

def tap_exact(items, wanted):
    wanted_cf = wanted.casefold()
    for text, x, y in items:
        if text.casefold() == wanted_cf:
            run('adb', 'shell', 'input', 'tap', str(x), str(y))
            print(f'tapped={text}')
            return True
    return False

root = dump()
items = nodes(root)
all_text = '\n'.join(text for text, _, _ in items)
print(all_text)
if 'keeps stopping' in all_text.casefold():
    raise SystemExit(20)

language_prompt = any(
    phrase in all_text.casefold()
    for phrase in ('choose a language', 'select a language', 'language selection')
)
if language_prompt and tap_exact(items, 'English'):
    time.sleep(3)
    root = dump()
    items = nodes(root)

# Advance only exact, conventional first-run actions. Do not tap partial text
# or arbitrary content cards.
for candidate in ('Continue', 'Get Started', 'Start', 'Next', 'OK', 'Not now', 'Skip'):
    if tap_exact(items, candidate):
        time.sleep(3)
        break
PY

stable=0
for attempt in $(seq 1 90); do
  adb shell dumpsys activity activities > "$EVIDENCE/activity-${attempt}.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/window-${attempt}.txt" 2>&1 || true
  adb logcat -d > "$EVIDENCE/logcat-${attempt}.txt" 2>&1 || true

  if grep -A120 'FATAL EXCEPTION' "$EVIDENCE/logcat-${attempt}.txt" \
      | grep -q 'Process: org.jw.jwlibrary.mobile'; then
    tail -n 500 "$EVIDENCE/logcat-${attempt}.txt" > "$EVIDENCE/fatal-logcat.txt"
    echo 'JW Library crashed during normal first-run initialization.' >&2
    exit 1
  fi

  foreground=false
  if grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
      "$EVIDENCE/activity-${attempt}.txt" >/dev/null; then
    foreground=true
  fi

  if (( attempt % 4 == 0 )); then
    set +e
    timeout 45s python3 /tmp/jw-bootstrap-ui.py \
      > "$EVIDENCE/ui-${attempt}.txt" 2>&1
    UI_STATUS=$?
    set -e
    if [[ "$UI_STATUS" == 20 ]]; then
      echo 'Android reported that JW Library stopped during first-run initialization.' >&2
      exit 1
    fi
  fi

  if [[ "$foreground" == true ]] && adb shell pidof "$JW_PACKAGE" >/dev/null 2>&1; then
    stable=$((stable + 1))
  else
    stable=0
    adb shell input keyevent 4 >/dev/null 2>&1 || true
    adb shell am start -n "$JW_COMPONENT" >/dev/null 2>&1 || true
  fi

  if (( stable >= 10 )); then
    adb exec-out screencap -p > "$EVIDENCE/initialized.png"
    adb shell dumpsys activity activities > "$EVIDENCE/initialized-activity.txt"
    adb logcat -d > "$EVIDENCE/initialized-logcat.txt"
    printf '%s\n' 'PASS: official JW Library completed a normal first-run launch, remained foreground and alive for ten consecutive checks, and produced no package-specific fatal exception before Finder testing.' \
      | tee "$EVIDENCE/RESULT.txt"
    break
  fi

  [[ "$attempt" != 90 ]] || { echo 'JW Library never reached a stable initialized foreground state.' >&2; exit 1; }
  sleep 2
done

adb shell am force-stop "$JW_PACKAGE"
exec bash .msc-build/installed-phone-jw-0121-core.sh "$@"
