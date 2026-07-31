#!/usr/bin/env bash
set -euo pipefail

# Generate the full verifier from the last complete source, then harden the
# official JW Library first-run gate. Foreground alone is not initialization:
# TermsOfUseActivity must be completed and dismissed before Finder tests run.
PINNED_BASE='f9d5184929fa10a6f094983e6f26a58d8a519876'
BASE_PATH='.msc-build/installed-phone-jw-0121.sh'
GENERATED='/tmp/installed-phone-jw-0121-generated.sh'

git fetch --no-tags --depth=1 origin "$PINNED_BASE" >/dev/null 2>&1
git show "${PINNED_BASE}:${BASE_PATH}" > /tmp/installed-phone-jw-0121-base.sh

python3 - <<'PY'
from pathlib import Path

source = Path('/tmp/installed-phone-jw-0121-base.sh').read_text(encoding='utf-8')
anchor = "\nstable=0\n"
if source.count(anchor) != 1:
    raise SystemExit('Expected one JW Library stability-loop anchor.')

acceptance = r'''
accept_terms_if_present() {
  local activity_file="$1" step action x y state_file signature last_signature='' stale_count=0 swipe_count
  if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.TermsOfUseActivity' "$activity_file"; then
    return 0
  fi

  echo 'JW Library Terms of Use is active; scrolling the legal WebView itself until ACCEPT is enabled.' \
    | tee -a "$EVIDENCE/terms-acceptance.txt"

  for step in $(seq 1 50); do
    adb shell rm -f /sdcard/jw-terms.xml >/dev/null 2>&1 || true
    action=''
    if timeout 45s adb shell uiautomator dump --compressed /sdcard/jw-terms.xml \
        > "$EVIDENCE/terms-dump-${step}.txt" 2>&1 \
      && adb pull /sdcard/jw-terms.xml /tmp/jw-terms.xml >/dev/null 2>&1; then
      action="$(python3 - <<'PY2'
import re
import xml.etree.ElementTree as ET
from pathlib import Path
path = Path('/tmp/jw-terms.xml')
if path.exists():
    root = ET.parse(path).getroot()
    accept = None
    visible_ids = []
    for node in root.iter('node'):
        text = (node.attrib.get('text') or node.attrib.get('content-desc') or '').strip()
        match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        x, y = (left + right)//2, (top + bottom)//2
        folded = text.casefold()
        if folded == 'accept' and node.attrib.get('enabled', 'true') == 'true' \
                and node.attrib.get('clickable') == 'true' and right > left and bottom > top:
            accept = ('accept', x, y)
        resource_id = node.attrib.get('resource-id', '')
        if resource_id.startswith('p') and resource_id[1:].isdigit() \
                and right > 173 and left < 907 and bottom > 757 and top < 1611:
            visible_ids.append(resource_id)
    if accept:
        print(*accept)
    else:
        ordered = []
        for item in visible_ids:
            if item not in ordered:
                ordered.append(item)
        print('scroll', ','.join(ordered) if ordered else 'unknown')
PY2
      )"
    fi

    if [[ "$action" == accept\ * ]]; then
      read -r action x y <<< "$action"
      adb shell input tap "$x" "$y"
      printf 'JW terms action accept at %s,%s on step %d.\n' "$x" "$y" "$step" \
        | tee -a "$EVIDENCE/terms-acceptance.txt"
      sleep 3
    else
      signature="${action#scroll }"
      if [[ -z "$signature" || "$signature" == "$action" ]]; then
        signature='dump-unavailable'
      fi
      if [[ "$signature" == "$last_signature" ]]; then
        stale_count=$((stale_count + 1))
      else
        stale_count=0
      fi
      last_signature="$signature"
      swipe_count=2
      if (( stale_count >= 2 )); then
        swipe_count=4
      fi
      for swipe in $(seq 1 "$swipe_count"); do
        if (( swipe % 2 == 1 )); then
          adb shell input swipe 350 1440 350 820 220 >/dev/null 2>&1 || true
        else
          adb shell input swipe 730 1440 730 820 220 >/dev/null 2>&1 || true
        fi
        sleep 0.5
      done
      printf 'JW terms WebView swipes=%d step=%d visible=%s stale=%d.\n' \
        "$swipe_count" "$step" "$signature" "$stale_count" \
        | tee -a "$EVIDENCE/terms-acceptance.txt"
      sleep 1
    fi

    state_file="$EVIDENCE/terms-state-${step}.txt"
    adb shell dumpsys activity activities > "$state_file" 2>&1 || true
    adb logcat -d > "$EVIDENCE/terms-logcat-${step}.txt" 2>&1 || true
    if grep -A120 'FATAL EXCEPTION' "$EVIDENCE/terms-logcat-${step}.txt" \
        | grep -q 'Process: org.jw.jwlibrary.mobile'; then
      echo 'Official JW Library crashed while its Terms of Use was being completed.' >&2
      return 1
    fi

    if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.TermsOfUseActivity' "$state_file" \
      && grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
        "$state_file" >/dev/null; then
      adb exec-out screencap -p > "$EVIDENCE/terms-after-accept.png" || true
      echo 'PASS: official Terms of Use was accepted and TermsOfUseActivity closed.' \
        | tee -a "$EVIDENCE/terms-acceptance.txt"
      return 0
    fi

    # If Android unexpectedly left JW Library, restore the exact first-run
    # activity and continue. This is evidence-preserving recovery, not a pass.
    if ! grep -q 'org\.jw\.jwlibrary\.mobile' "$state_file"; then
      echo "JW Library lost foreground during terms step ${step}; relaunching first-run activity." \
        | tee -a "$EVIDENCE/terms-acceptance.txt"
      adb shell am start -n "$JW_COMPONENT" >/dev/null 2>&1 || true
      sleep 4
    fi
  done

  adb shell uiautomator dump /sdcard/jw-terms-final.xml \
    > "$EVIDENCE/terms-final-dump.txt" 2>&1 || true
  adb pull /sdcard/jw-terms-final.xml "$EVIDENCE/terms-final.xml" >/dev/null 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/terms-failure.png" || true
  echo 'JW Library Terms of Use could not be completed on the test emulator.' >&2
  return 1
}
'''
source = source.replace(anchor, acceptance + anchor, 1)

ui_status = """    if [[ "$UI_STATUS" == 20 ]]; then
      echo 'Android reported that JW Library stopped during first-run initialization.' >&2
      exit 1
    fi
"""
if source.count(ui_status) != 1:
    raise SystemExit('Expected one JW bootstrap UI-status guard.')
source = source.replace(
    ui_status,
    ui_status + """    if ! accept_terms_if_present "$EVIDENCE/activity-${attempt}.txt"; then
      exit 1
    fi
""",
    1,
)

old_stable = """  if [[ "$foreground" == true ]] && adb shell pidof "$JW_PACKAGE" >/dev/null 2>&1; then
    stable=$((stable + 1))
"""
new_stable = """  if [[ "$foreground" == true ]] \\
    && ! grep -q 'org\\.jw\\.jwlibrary\\.mobile/.activity\\.TermsOfUseActivity' "$EVIDENCE/activity-${attempt}.txt" \\
    && adb shell pidof "$JW_PACKAGE" >/dev/null 2>&1; then
    stable=$((stable + 1))
"""
if source.count(old_stable) != 1:
    raise SystemExit('Expected one JW foreground stability assertion.')
source = source.replace(old_stable, new_stable, 1)

old_pass = "PASS: official JW Library completed a normal first-run launch, remained foreground and alive for ten consecutive checks, and produced no package-specific fatal exception before Finder testing."
new_pass = "PASS: official JW Library completed first-run setup, its Terms of Use was accepted and dismissed, it remained in a non-terms foreground state for ten consecutive checks, and it produced no package-specific fatal exception before Finder testing."
if source.count(old_pass) != 1:
    raise SystemExit('Expected the original JW initialization result text.')
source = source.replace(old_pass, new_pass, 1)

Path('/tmp/installed-phone-jw-0121-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash "$GENERATED" "$@"
