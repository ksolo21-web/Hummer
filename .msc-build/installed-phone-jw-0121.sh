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
  local activity_file="$1" scroll coords x y after_file
  if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.TermsOfUseActivity' "$activity_file"; then
    return 0
  fi

  echo 'JW Library Terms of Use is active; scrolling to the official ACCEPT control.' \
    | tee -a "$EVIDENCE/terms-acceptance.txt"
  for scroll in $(seq 1 40); do
    adb shell rm -f /sdcard/jw-terms.xml >/dev/null 2>&1 || true
    if timeout 45s adb shell uiautomator dump --compressed /sdcard/jw-terms.xml \
        > "$EVIDENCE/terms-dump-${scroll}.txt" 2>&1 \
      && adb pull /sdcard/jw-terms.xml /tmp/jw-terms.xml >/dev/null 2>&1; then
      coords="$(python3 - <<'PY2'
import re
import xml.etree.ElementTree as ET
from pathlib import Path
path = Path('/tmp/jw-terms.xml')
if path.exists():
    for node in ET.parse(path).getroot().iter('node'):
        text = (node.attrib.get('text') or node.attrib.get('content-desc') or '').strip()
        if text.casefold() != 'accept':
            continue
        if node.attrib.get('enabled', 'true') != 'true':
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
      if [[ -n "$coords" ]]; then
        read -r x y <<< "$coords"
        adb shell input tap "$x" "$y"
        printf 'Tapped official ACCEPT control at %s,%s after scroll %d.\n' "$x" "$y" "$scroll" \
          | tee -a "$EVIDENCE/terms-acceptance.txt"
        sleep 6
        after_file="$EVIDENCE/terms-after-accept-${scroll}.txt"
        adb shell dumpsys activity activities > "$after_file" 2>&1 || true
        adb exec-out screencap -p > "$EVIDENCE/terms-after-accept.png" || true
        if ! grep -q 'org\.jw\.jwlibrary\.mobile/.activity\.TermsOfUseActivity' "$after_file" \
          && grep -E 'mResumedActivity=.*org\.jw\.jwlibrary\.mobile|topResumedActivity=.*org\.jw\.jwlibrary\.mobile|ResumedActivity: ActivityRecord.*org\.jw\.jwlibrary\.mobile|Resumed: ActivityRecord.*org\.jw\.jwlibrary\.mobile' \
            "$after_file" >/dev/null; then
          echo 'PASS: official Terms of Use was accepted and TermsOfUseActivity closed.' \
            | tee -a "$EVIDENCE/terms-acceptance.txt"
          return 0
        fi
      fi
    fi

    # The ACCEPT control remains disabled until the legal text has actually
    # been scrolled to its end. Use a human-equivalent upward swipe.
    adb shell input swipe 540 1950 540 350 180 >/dev/null 2>&1 || true
    sleep 1
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
