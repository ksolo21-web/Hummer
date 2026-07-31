#!/usr/bin/env bash
set -euo pipefail

# Generate from the last complete retry-safe verifier, then harden two pieces
# of runtime verification:
# - compact layouts use More while expanded layouts expose AI Study directly;
# - a hosted-emulator Pixel Launcher ANR is recovered explicitly so its system
#   dialog cannot cover an otherwise healthy My Study Companion onboarding UI.
PINNED_BASE='1761f2b654cbc3e96a5735dec752ef3032e8abdd'
BASE_PATH='.msc-build/installed-phone-migration-0121.sh'
GENERATED='/tmp/installed-phone-migration-0121-generated.sh'

git fetch --no-tags --depth=1 origin "$PINNED_BASE" >/dev/null 2>&1
git show "${PINNED_BASE}:${BASE_PATH}" > /tmp/installed-phone-migration-0121-base.sh

python3 - <<'PY'
from pathlib import Path

source = Path('/tmp/installed-phone-migration-0121-base.sh').read_text(encoding='utf-8')

home_anchor = "ensure_home_navigation() {\n"
if source.count(home_anchor) != 1:
    raise SystemExit('Expected one home-navigation function anchor.')
recovery = r'''recover_pixel_launcher_anr_if_present() {
  local dump_file="/tmp/msc-system-dialog.xml" coords x y
  adb shell rm -f /sdcard/msc-system-dialog.xml >/dev/null 2>&1 || true
  if ! timeout 45s adb shell uiautomator dump --compressed /sdcard/msc-system-dialog.xml \
      > "$EVIDENCE/system-dialog-dump.txt" 2>&1 \
    || ! adb pull /sdcard/msc-system-dialog.xml "$dump_file" >/dev/null 2>&1; then
    return 0
  fi

  if ! grep -q "Pixel Launcher isn't responding" "$dump_file"; then
    return 0
  fi

  cp "$dump_file" "$EVIDENCE/pixel-launcher-anr.xml"
  adb exec-out screencap -p > "$EVIDENCE/pixel-launcher-anr.png" || true
  coords="$(python3 - <<'PY2'
import re
import xml.etree.ElementTree as ET
from pathlib import Path
path = Path('/tmp/msc-system-dialog.xml')
if path.exists():
    for node in ET.parse(path).getroot().iter('node'):
        if node.attrib.get('resource-id') != 'android:id/aerr_close':
            continue
        if node.attrib.get('clickable') != 'true' or node.attrib.get('enabled', 'true') != 'true':
            continue
        match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        x, y = (left + right)//2, (top + bottom)//2
        if right > left and bottom > top:
            print(f'{x} {y}')
            break
PY2
  )"
  if [[ -z "$coords" ]]; then
    echo 'Pixel Launcher ANR was visible but its exact Close app control was unavailable.' >&2
    return 1
  fi

  read -r x y <<< "$coords"
  adb shell input tap "$x" "$y"
  sleep 3
  adb shell am force-stop com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
  sleep 2
  launch_package
  printf 'PASS: dismissed exact Pixel Launcher ANR dialog and restored My Study Companion foreground.\n' \
    | tee -a "$EVIDENCE/system-dialog-recovery.txt"
}

'''
source = source.replace(home_anchor, recovery + home_anchor, 1)

old_home = """    if python3 /tmp/msc-ui.py exists 'More'; then
      printf 'PASS: home navigation available after UI check %d.\\n' "$attempt" | tee -a "$EVIDENCE/home-navigation.txt"
      return 0
    fi
"""
new_home = """    recover_pixel_launcher_anr_if_present
    if python3 /tmp/msc-ui.py exists 'More' || python3 /tmp/msc-ui.py exists 'AI Study'; then
      printf 'PASS: adaptive home navigation available after UI check %d.\\n' "$attempt" | tee -a "$EVIDENCE/home-navigation.txt"
      return 0
    fi
"""
if source.count(old_home) != 1:
    raise SystemExit('Expected one compact-only home-navigation assertion.')
source = source.replace(old_home, new_home, 1)

old_tap_loop = """  for attempt in $(seq 1 30); do
    # Find and tap from one hierarchy snapshot. A separate exists/tap pair is
"""
new_tap_loop = """  for attempt in $(seq 1 30); do
    recover_pixel_launcher_anr_if_present
    # Find and tap from one hierarchy snapshot. A separate exists/tap pair is
"""
if source.count(old_tap_loop) != 1:
    raise SystemExit('Expected one retry-safe UI tap loop.')
source = source.replace(old_tap_loop, new_tap_loop, 1)

old_tablet = """launch_package
ensure_home_navigation
tap_with_retry 'More' 'tablet More navigation'
tap_with_retry 'AI Study Assistant' 'tablet AI Study Assistant'
python3 /tmp/msc-ui.py assert 'Source protection'
"""
new_tablet = """launch_package
ensure_home_navigation
if python3 /tmp/msc-ui.py exists 'AI Study'; then
  tap_with_retry 'AI Study' 'tablet AI Study rail navigation'
else
  tap_with_retry 'More' 'tablet More navigation fallback'
  tap_with_retry 'AI Study Assistant' 'tablet AI Study Assistant fallback'
fi
python3 /tmp/msc-ui.py assert 'Source protection'
"""
if source.count(old_tablet) != 1:
    raise SystemExit('Expected one tablet compact-navigation sequence.')
source = source.replace(old_tablet, new_tablet, 1)

old_result = "launched on phone and tablet geometry, displayed the hardened AI source-protection UI"
new_result = "launched on phone and tablet geometry, recovered any exact Pixel Launcher ANR without masking app failures, used compact bottom navigation on phone and the expanded AI Study rail on tablet, displayed the hardened AI source-protection UI"
if source.count(old_result) != 1:
    raise SystemExit('Expected migration result statement.')
source = source.replace(old_result, new_result, 1)

Path('/tmp/installed-phone-migration-0121-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash "$GENERATED"
