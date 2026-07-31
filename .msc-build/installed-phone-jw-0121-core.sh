#!/usr/bin/env bash
set -euo pipefail

# Generate the complete strict verifier from the last full source, then apply
# evidence-backed harness corrections:
# 1. Split strict-shell local declarations so nounset does not expand an
#    evidence-derived path before its source variable is assigned.
# 2. Treat JW Library's delayed Privacy Settings activity as first-run setup,
#    choose the privacy-minimizing DECLINE option, prove the modal closed, and
#    require the actual requested content to regain focus before testing Back.
# 3. Address the exact clickable Android dialog button (android:id/button2)
#    instead of matching the word "decline" inside the explanatory paragraph.
PINNED_BASE='ffa565c8242ca868233b647da68b8f23315ed743'
BASE_PATH='.msc-build/installed-phone-jw-0121-core.sh'
GENERATED='/tmp/installed-phone-jw-0121-core-generated.sh'

git fetch --no-tags --depth=1 origin "$PINNED_BASE" >/dev/null 2>&1
git show "${PINNED_BASE}:${BASE_PATH}" > /tmp/installed-phone-jw-0121-core-base.sh

python3 - <<'PY'
from pathlib import Path

path = Path('/tmp/installed-phone-jw-0121-core-base.sh')
source = path.read_text(encoding='utf-8')

old_local = '  local evidence_file="$1" label="$2" attempt stable=0 window_file="${evidence_file%.txt}-window.txt"\n'
new_local = (
    '  local evidence_file="$1" label="$2" attempt stable=0\n'
    '  local window_file="${evidence_file%.txt}-window.txt"\n'
)
count = source.count(old_local)
if count != 2:
    raise SystemExit(f'Expected two strict-shell foreground declarations, found {count}.')
source = source.replace(old_local, new_local)

foreground_anchor = 'wait_for_jw_foreground() {\n'
if source.count(foreground_anchor) != 1:
    raise SystemExit('Expected one JW foreground function anchor.')
privacy_helper = r'''dismiss_jw_privacy_if_present() {
  local evidence_file="$1" label="$2" attempt coords x y
  local state_file="${evidence_file%.txt}-privacy-state.txt"
  local window_file="${evidence_file%.txt}-privacy-window.txt"

  adb shell dumpsys activity activities > "$evidence_file" 2>&1 || true
  if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.PrivacyAcceptanceActivity' "$evidence_file"; then
    return 0
  fi

  printf 'JW Library Privacy Settings appeared during %s; choosing DECLINE for optional diagnostics.\n' "$label" \
    | tee -a "$EVIDENCE/privacy-acceptance.txt"
  for attempt in $(seq 1 20); do
    coords=''
    adb shell rm -f /sdcard/jw-privacy.xml >/dev/null 2>&1 || true
    if timeout 45s adb shell uiautomator dump --compressed /sdcard/jw-privacy.xml \
        > "$EVIDENCE/privacy-dump-${attempt}.txt" 2>&1 \
      && adb pull /sdcard/jw-privacy.xml /tmp/jw-privacy.xml >/dev/null 2>&1; then
      coords="$(python3 - <<'PY2'
import re
import xml.etree.ElementTree as ET
from pathlib import Path
path = Path('/tmp/jw-privacy.xml')
if path.exists():
    for node in ET.parse(path).getroot().iter('node'):
        text = (node.attrib.get('text') or node.attrib.get('content-desc') or '').strip()
        resource = node.attrib.get('resource-id', '')
        if resource != 'android:id/button2' and text.casefold() != 'decline':
            continue
        if node.attrib.get('clickable') != 'true' or node.attrib.get('enabled', 'true') != 'true':
            continue
        match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        x, y = (left + right)//2, (top + bottom)//2
        if right > left and bottom > top and 0 <= x <= 2000 and 0 <= y <= 3000:
            print(f'{x} {y}')
            break
PY2
      )"
    fi

    if [[ -n "$coords" ]]; then
      read -r x y <<< "$coords"
      adb shell input tap "$x" "$y"
      printf 'Tapped exact clickable DECLINE button at %s,%s during %s on attempt %d.\n' "$x" "$y" "$label" "$attempt" \
        | tee -a "$EVIDENCE/privacy-acceptance.txt"
      sleep 4
    fi

    adb shell dumpsys activity activities > "$state_file" 2>&1 || true
    adb shell dumpsys window windows > "$window_file" 2>&1 || true
    adb logcat -d > "$EVIDENCE/privacy-${attempt}-logcat.txt" 2>&1 || true
    assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/privacy-${attempt}-logcat.txt" \
      "JW Library Privacy Settings during ${label}"

    if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.PrivacyAcceptanceActivity' "$state_file" \
      && grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
        "$state_file" >/dev/null \
      && grep -E 'mCurrentFocus=.*org\.jw\.jwlibrary\.mobile|mFocusedApp=.*org\.jw\.jwlibrary\.mobile' \
        "$state_file" "$window_file" >/dev/null; then
      adb exec-out screencap -p > "$EVIDENCE/privacy-after-decline.png" || true
      printf 'PASS: JW Library Privacy Settings closed and actual JW content regained focus during %s.\n' "$label" \
        | tee -a "$EVIDENCE/privacy-acceptance.txt"
      return 0
    fi
    sleep 2
  done

  adb shell uiautomator dump /sdcard/jw-privacy-final.xml \
    > "$EVIDENCE/privacy-final-dump.txt" 2>&1 || true
  adb pull /sdcard/jw-privacy-final.xml "$EVIDENCE/privacy-final.xml" >/dev/null 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/privacy-failure.png" || true
  echo "JW Library Privacy Settings could not be dismissed during ${label}." >&2
  return 1
}

'''
source = source.replace(foreground_anchor, privacy_helper + foreground_anchor, 1)

old_loop = '''    adb shell dumpsys activity activities > "$evidence_file"
    adb shell dumpsys window windows > "$window_file"
    if grep -E 'mResumedActivity=.*org\\.jw\\.jwlibrary\\.mobile|topResumedActivity=.*org\\.jw\\.jwlibrary\\.mobile|ResumedActivity: ActivityRecord.*org\\.jw\\.jwlibrary\\.mobile|Resumed: ActivityRecord.*org\\.jw\\.jwlibrary\\.mobile' \\
'''
new_loop = '''    adb shell dumpsys activity activities > "$evidence_file"
    adb shell dumpsys window windows > "$window_file"
    if grep -q 'org\\.jw\\.jwlibrary\\.mobile/.activity\\.PrivacyAcceptanceActivity' "$evidence_file"; then
      dismiss_jw_privacy_if_present "$evidence_file" "$label"
      stable=0
      sleep 2
      continue
    fi
    if grep -E 'mResumedActivity=.*org\\.jw\\.jwlibrary\\.mobile|topResumedActivity=.*org\\.jw\\.jwlibrary\\.mobile|ResumedActivity: ActivityRecord.*org\\.jw\\.jwlibrary\\.mobile|Resumed: ActivityRecord.*org\\.jw\\.jwlibrary\\.mobile' \\
'''
if source.count(old_loop) != 1:
    raise SystemExit('Expected one JW foreground loop anchor.')
source = source.replace(old_loop, new_loop, 1)

old_modal_guard = '''      && ! grep -Eq 'Application Error: org\\.jw\\.jwlibrary\\.mobile|mCurrentFocus=.*Application Error' \\
        "$evidence_file" "$window_file"; then
'''
new_modal_guard = '''      && ! grep -Eq 'Application Error: org\\.jw\\.jwlibrary\\.mobile|mCurrentFocus=.*Application Error|TermsOfUseActivity|PrivacyAcceptanceActivity' \\
        "$evidence_file" "$window_file"; then
'''
if source.count(old_modal_guard) != 1:
    raise SystemExit('Expected one JW modal/crash foreground guard.')
source = source.replace(old_modal_guard, new_modal_guard, 1)

old_return = '''adb logcat -d > "$EVIDENCE/journey-jw-logcat.txt"
assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/journey-jw-logcat.txt" 'Bible Journey Day 1 in official JW Library'
adb shell input keyevent 4
wait_for_phone_foreground "$EVIDENCE/journey-return-activity.txt" 'My Study Companion after returning from JW Library'
'''
new_return = '''adb logcat -d > "$EVIDENCE/journey-jw-logcat.txt"
assert_no_package_fatal "$JW_PACKAGE" "$EVIDENCE/journey-jw-logcat.txt" 'Bible Journey Day 1 in official JW Library'
dismiss_jw_privacy_if_present "$EVIDENCE/journey-pre-return-activity.txt" 'Bible Journey Day 1 before returning'
wait_for_jw_foreground "$EVIDENCE/journey-content-ready-activity.txt" 'Bible Journey Day 1 content after first-run privacy setup'
adb exec-out screencap -p > "$EVIDENCE/journey-content-ready.png" || true
adb shell input keyevent 4
wait_for_phone_foreground "$EVIDENCE/journey-return-activity.txt" 'My Study Companion after returning from JW Library'
'''
if source.count(old_return) != 1:
    raise SystemExit('Expected one Bible Journey return sequence.')
source = source.replace(old_return, new_return, 1)

old_result = 'Bible Journey Day 1 opened JW Library and returned without losing state; My Study Companion produced no package-specific fatal exception.'
new_result = 'Bible Journey Day 1 opened actual JW content, the delayed official Privacy Settings prompt was declined through its exact clickable dialog button and dismissed when present, and Back returned to My Study Companion without losing state; My Study Companion produced no package-specific fatal exception.'
if source.count(old_result) != 1:
    raise SystemExit('Expected one JW result statement.')
source = source.replace(old_result, new_result, 1)

Path('/tmp/installed-phone-jw-0121-core-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash "$GENERATED" "$@"
