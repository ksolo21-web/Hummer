#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-wear-0120.sh').read_text(encoding='utf-8')
source = source.replace('installed-0120-evidence/wear', 'installed-0121-evidence/wear')
source = source.replace('MyStudyCompanion-wear-0.12.0-debug.apk', 'MyStudyCompanion-wear-0.12.1-debug.apk')
source = source.replace('PASS: 0.12.0 Wear APK', 'PASS: 0.12.1 Wear APK')
Path('/tmp/installed-wear-0121-generated.sh').write_text(source, encoding='utf-8')
PY
bash /tmp/installed-wear-0121-generated.sh

EVIDENCE='installed-0121-evidence/wear'
PACKAGE='com.mystudycompanion.app.debug'
COMPONENT="${PACKAGE}/com.mystudycompanion.app.wear.MainActivity"
adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 2147483647 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell am start -W -n "$COMPONENT" | tee "$EVIDENCE/visual-launch.txt"
grep -q 'Status: ok' "$EVIDENCE/visual-launch.txt"
sleep 2
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell uiautomator dump /sdcard/watch-awake.xml | tee "$EVIDENCE/ui-dump.txt"
adb pull /sdcard/watch-awake.xml "$EVIDENCE/watch-awake.xml"
grep -Eq 'My Study Companion|TODAY.S TEXT|BIBLE JOURNEY' "$EVIDENCE/watch-awake.xml"
adb exec-out screencap -p > "$EVIDENCE/watch-awake.png"
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
printf '%s\n' 'PASS: Wear UI hierarchy exposed My Study Companion content and the captured screenshot was not black.' \
  | tee -a "$EVIDENCE/RESULT.txt"
