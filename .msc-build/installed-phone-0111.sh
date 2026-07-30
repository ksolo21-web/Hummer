#!/usr/bin/env bash
set -euo pipefail
mkdir -p installed-evidence

adb_recover() {
  local attempt
  for attempt in 1 2 3 4 5; do
    adb start-server >/dev/null 2>&1 || true
    adb reconnect >/dev/null 2>&1 || true
    if adb wait-for-device >/dev/null 2>&1 \
      && adb shell 'echo ready' 2>/dev/null | grep -q ready; then
      return 0
    fi
    sleep $((attempt * 4))
  done
  echo 'ADB did not recover after five attempts.' >&2
  return 1
}

adb_install_retry() {
  local apk="$1" log="$2" attempt
  for attempt in 1 2 3 4; do
    echo "Install attempt ${attempt}: ${apk}" | tee -a "$log"
    adb_recover
    if adb install -r "$apk" 2>&1 | tee -a "$log"; then
      return 0
    fi
    adb kill-server >/dev/null 2>&1 || true
    sleep $((attempt * 6))
  done
  echo "Failed to install ${apk} after four attempts." >&2
  return 1
}

adb_install_retry JWLibrary.apk installed-evidence/jw-install.txt
adb_install_retry dist/MyStudyCompanion-phone-0.11.1-debug.apk installed-evidence/phone-install.txt
adb_recover
test -n "$(adb shell pm path org.jw.jwlibrary.mobile | tr -d '\r')"
test -n "$(adb shell pm path com.mystudycompanion.app.debug | tr -d '\r')"

resolve_jw() {
  local name="$1" uri="$2" resolved
  adb_recover
  resolved="$(adb shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d "$uri" | tr -d '\r')"
  printf '%s -> %s\n' "$uri" "$resolved" | tee "installed-evidence/${name}-resolve.txt"
  grep -q 'org.jw.jwlibrary.mobile' "installed-evidence/${name}-resolve.txt"
}
resolve_jw job1 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=18001000'
resolve_jw jeremiah 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=24020007-24020018'
resolve_jw week 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'
resolve_jw research 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=rsg19'
resolve_jw week_https 'https://www.jw.org/finder?srcid=jwlshare&wtlocale=E&prefer=lang&docid=202026244'

adb_recover
adb shell am start -W -a android.intent.action.VIEW \
  -d 'jwlibrary:///finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=18001000' \
  org.jw.jwlibrary.mobile | tee installed-evidence/jw-start.txt
grep -q 'Status: ok' installed-evidence/jw-start.txt
sleep 8
adb shell dumpsys activity activities > installed-evidence/jw-activity.txt
grep -m1 'mResumedActivity' installed-evidence/jw-activity.txt | grep -q 'org.jw.jwlibrary.mobile'
adb exec-out screencap -p > installed-evidence/jw-job1.png
adb shell uiautomator dump /sdcard/jw-job1.xml >/dev/null 2>&1 || true
adb pull /sdcard/jw-job1.xml installed-evidence/jw-job1.xml >/dev/null 2>&1 || true

cat > /tmp/ui.py <<'PY'
import re, subprocess, sys, time, xml.etree.ElementTree as ET

def dump():
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/window.xml'], check=True, stdout=subprocess.DEVNULL)
    subprocess.run(['adb','pull','/sdcard/window.xml','/tmp/window.xml'], check=True, stdout=subprocess.DEVNULL)
    return ET.parse('/tmp/window.xml').getroot()

def find(text):
    root = dump()
    for node in root.iter('node'):
        value = (node.attrib.get('text') or node.attrib.get('content-desc') or '').strip()
        if value == text or text in value:
            match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
            if match:
                left, top, right, bottom = map(int, match.groups())
                return ((left + right)//2, (top + bottom)//2, value)
    return None

def tap(text):
    hit = find(text)
    if not hit:
        raise SystemExit(f'UI text not found: {text}')
    subprocess.run(['adb','shell','input','tap',str(hit[0]),str(hit[1])], check=True)
    time.sleep(2)

def assert_text(text):
    if not find(text):
        raise SystemExit(f'Expected UI text not found: {text}')

command = sys.argv[1]
if command == 'tap': tap(sys.argv[2])
elif command == 'assert': assert_text(sys.argv[2])
else: raise SystemExit(f'Unknown command: {command}')
PY

launch_companion() {
  adb_recover
  adb shell am force-stop com.mystudycompanion.app.debug
  adb shell monkey -p com.mystudycompanion.app.debug -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 5
}

launch_companion
python3 /tmp/ui.py tap 'Enter owner-only private alpha'
python3 /tmp/ui.py tap 'More'
for _ in 1 2 3 4; do adb shell input swipe 500 1500 500 450 350; sleep 1; done
python3 /tmp/ui.py assert 'Settings'
adb exec-out screencap -p > installed-evidence/phone-more-settings.png

launch_companion
python3 /tmp/ui.py tap 'More'
python3 /tmp/ui.py tap 'Personal Study Companion'
python3 /tmp/ui.py assert 'Research'
python3 /tmp/ui.py tap 'Bible'
for _ in 1 2 3; do adb shell input swipe 500 1500 500 650 300; sleep 1; done
python3 /tmp/ui.py tap 'Start journey at Day 1'
python3 /tmp/ui.py assert 'Day 1 of'
python3 /tmp/ui.py assert 'Read now in JW Library'
adb exec-out screencap -p > installed-evidence/journey-day1.png
python3 /tmp/ui.py tap 'Read now in JW Library'
sleep 6
adb shell dumpsys activity activities > installed-evidence/journey-jw-activity.txt
grep -m1 'mResumedActivity' installed-evidence/journey-jw-activity.txt | grep -q 'org.jw.jwlibrary.mobile'
adb exec-out screencap -p > installed-evidence/journey-jw-open.png
adb shell input keyevent 4
sleep 3
adb shell dumpsys activity activities > installed-evidence/journey-return-activity.txt
grep -m1 'mResumedActivity' installed-evidence/journey-return-activity.txt | grep -q 'com.mystudycompanion.app.debug'
python3 /tmp/ui.py assert 'Day 1 of'

adb shell wm size 1600x2560
adb shell wm density 320
launch_companion
python3 /tmp/ui.py tap 'More'
python3 /tmp/ui.py assert 'Settings'
python3 /tmp/ui.py tap 'Personal Study Companion'
python3 /tmp/ui.py assert 'Research'
adb exec-out screencap -p > installed-evidence/tablet-companion.png
adb shell uiautomator dump /sdcard/tablet.xml >/dev/null
adb pull /sdcard/tablet.xml installed-evidence/tablet.xml >/dev/null

echo 'Phone APK, official JW Library targets, external-app return, scrolling, and tablet layout passed.' \
  | tee installed-evidence/RESULT.txt
