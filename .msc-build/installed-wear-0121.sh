#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-wear-0120.sh').read_text(encoding='utf-8')
source = source.replace('installed-0120-evidence/wear', 'installed-0121-evidence/wear')
source = source.replace('MyStudyCompanion-wear-0.12.0-debug.apk', 'MyStudyCompanion-wear-0.12.1-debug.apk')
source = source.replace('PASS: 0.12.0 Wear APK', 'PASS: 0.12.1 Wear APK')
blocking = """adb shell am start -W -n "$COMPONENT" | tee "$EVIDENCE/launch.txt"
grep -q 'Status: ok' "$EVIDENCE/launch.txt"
"""
nonblocking = """adb shell am start -n "$COMPONENT" | tee "$EVIDENCE/launch.txt"
grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/launch.txt"
"""
if source.count(blocking) != 1:
    raise SystemExit('Expected exactly one blocking Wear launch assertion.')
source = source.replace(blocking, nonblocking, 1)
Path('/tmp/installed-wear-0121-generated.sh').write_text(source, encoding='utf-8')
PY
bash /tmp/installed-wear-0121-generated.sh

EVIDENCE='installed-0121-evidence/wear'
PACKAGE='com.mystudycompanion.app.debug'
COMPONENT="${PACKAGE}/com.mystudycompanion.app.wear.MainActivity"
REMOTE_XML='/sdcard/watch-awake.xml'
LOCAL_XML="$EVIDENCE/watch-awake.xml"
FINAL_PNG="$EVIDENCE/watch-awake.png"

adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 2147483647 || true
adb shell settings put global device_provisioned 1 || true
adb shell settings put secure user_setup_complete 1 || true
adb shell am force-stop com.google.android.wearable.setupwizard || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true

hierarchy_ok=false
for attempt in $(seq 1 8); do
  printf 'Wear visual verification attempt %d/8.\n' "$attempt" | tee -a "$EVIDENCE/visual-retry.txt"
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell am start -n "$COMPONENT" | tee "$EVIDENCE/visual-launch-${attempt}.txt"
  grep -Eq 'Starting: Intent|Warning: Activity not started' "$EVIDENCE/visual-launch-${attempt}.txt"

  # Wait for the actual activity window, not the launch splash, to own focus.
  focused=false
  for settle in $(seq 1 20); do
    adb shell dumpsys activity activities > "$EVIDENCE/visual-activity-${attempt}.txt" 2>&1 || true
    adb shell dumpsys window windows > "$EVIDENCE/visual-window-${attempt}.txt" 2>&1 || true
    if grep -E "mResumedActivity=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity|Resumed: ActivityRecord.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity" \
        "$EVIDENCE/visual-activity-${attempt}.txt" >/dev/null \
      && grep -E "mCurrentFocus=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity|mFocusedApp=.*${PACKAGE}/com\.mystudycompanion\.app\.wear\.MainActivity" \
        "$EVIDENCE/visual-window-${attempt}.txt" >/dev/null \
      && ! grep -q "Splash Screen ${PACKAGE}" "$EVIDENCE/visual-window-${attempt}.txt"; then
      focused=true
      break
    fi
    sleep 2
  done
  if [[ "$focused" != true ]]; then
    echo "Attempt ${attempt}: real Wear activity window did not settle." | tee -a "$EVIDENCE/visual-retry.txt"
    adb shell input keyevent 3 >/dev/null 2>&1 || true
    sleep 2
    continue
  fi

  # Capture every settled attempt before asking UiAutomator for its hierarchy.
  adb exec-out screencap -p > "$EVIDENCE/watch-awake-attempt-${attempt}.png" || true
  adb shell rm -f "$REMOTE_XML" >/dev/null 2>&1 || true
  rm -f "$LOCAL_XML"
  dump_ok=false
  if timeout 45s adb shell uiautomator dump --compressed "$REMOTE_XML" \
      > "$EVIDENCE/ui-dump-${attempt}-compressed.txt" 2>&1; then
    dump_ok=true
  elif timeout 45s adb shell uiautomator dump "$REMOTE_XML" \
      > "$EVIDENCE/ui-dump-${attempt}-plain.txt" 2>&1; then
    dump_ok=true
  fi
  if [[ "$dump_ok" == true ]] \
    && adb shell test -s "$REMOTE_XML" \
    && adb pull "$REMOTE_XML" "$LOCAL_XML" > "$EVIDENCE/ui-pull-${attempt}.txt" 2>&1 \
    && grep -Eqi 'My Study Companion|TODAY.?S TEXT|BIBLE JOURNEY|FAMILY WORSHIP' "$LOCAL_XML"; then
    cp "$EVIDENCE/watch-awake-attempt-${attempt}.png" "$FINAL_PNG"
    cp "$EVIDENCE/visual-activity-${attempt}.txt" "$EVIDENCE/visual-activity.txt"
    cp "$EVIDENCE/visual-window-${attempt}.txt" "$EVIDENCE/visual-window.txt"
    hierarchy_ok=true
    printf 'PASS: Wear hierarchy exposed app content on attempt %d.\n' "$attempt" | tee -a "$EVIDENCE/visual-retry.txt"
    break
  fi

  echo "Attempt ${attempt}: UiAutomator did not return the rendered app hierarchy." | tee -a "$EVIDENCE/visual-retry.txt"
  adb shell input keyevent 3 >/dev/null 2>&1 || true
  sleep 2
done

if [[ "$hierarchy_ok" != true ]]; then
  adb shell dumpsys activity activities > "$EVIDENCE/visual-activity-final.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/visual-window-final.txt" 2>&1 || true
  adb logcat -d > "$EVIDENCE/visual-failure-logcat.txt" 2>&1 || true
  echo 'Wear app reached foreground, but the rendered hierarchy could not be captured after eight settled retries.' >&2
  exit 1
fi

python3 - <<'PY'
import struct, zlib
from pathlib import Path
p = Path('installed-0121-evidence/wear/watch-awake.png')
data = p.read_bytes()
if not data.startswith(b'\x89PNG\r\n\x1a\n'):
    raise SystemExit('Wear screenshot is not a PNG.')
pos = 8
w = h = bit = ctype = None
idat = bytearray()
while pos + 12 <= len(data):
    length = struct.unpack('>I', data[pos:pos+4])[0]
    kind = data[pos+4:pos+8]
    payload = data[pos+8:pos+8+length]
    pos += 12 + length
    if kind == b'IHDR':
        w, h, bit, ctype = struct.unpack('>IIBB', payload[:10])
    elif kind == b'IDAT':
        idat.extend(payload)
    elif kind == b'IEND':
        break
if bit != 8 or ctype not in (2, 6):
    raise SystemExit(f'Unsupported screenshot PNG format: bit={bit} color_type={ctype}')
bpp = 3 if ctype == 2 else 4
raw = zlib.decompress(bytes(idat))
stride = w * bpp
rows = []
offset = 0
prev = bytearray(stride)
for _ in range(h):
    f = raw[offset]
    offset += 1
    scan = bytearray(raw[offset:offset+stride])
    offset += stride
    recon = bytearray(stride)
    for i, value in enumerate(scan):
        a = recon[i-bpp] if i >= bpp else 0
        b = prev[i]
        c = prev[i-bpp] if i >= bpp else 0
        if f == 0:
            x = value
        elif f == 1:
            x = (value + a) & 255
        elif f == 2:
            x = (value + b) & 255
        elif f == 3:
            x = (value + ((a + b)//2)) & 255
        elif f == 4:
            q = a + b - c
            pa, pb, pc = abs(q-a), abs(q-b), abs(q-c)
            pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
            x = (value + pr) & 255
        else:
            raise SystemExit(f'Unsupported PNG filter {f}')
        recon[i] = x
    rows.append(recon)
    prev = recon
rgb_values = []
for row in rows:
    for i in range(0, len(row), bpp):
        rgb_values.extend(row[i:i+3])
nonzero = sum(v != 0 for v in rgb_values)
mean = sum(rgb_values) / max(1, len(rgb_values))
if nonzero < len(rgb_values) * 0.005 or mean < 1.0:
    raise SystemExit(f'Wear screenshot is effectively black: nonzero={nonzero}, mean={mean:.3f}')
Path('installed-0121-evidence/wear/visual-check.txt').write_text(
    f'PASS: {w}x{h} screenshot is visibly rendered; nonzero_rgb={nonzero}; mean_rgb={mean:.3f}.\n',
    encoding='utf-8',
)
PY
printf '%s\n' 'PASS: Wear UI hierarchy exposed My Study Companion content after the launch splash cleared, and the captured screenshot was visibly rendered rather than black.' \
  | tee -a "$EVIDENCE/RESULT.txt"
