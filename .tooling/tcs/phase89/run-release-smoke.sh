#!/usr/bin/env bash
set -euo pipefail

PKG=com.koenterprises.territorycardstudio
ACTIVITY="$PKG/.MainActivity"
APK=evidence/TerritoryCardStudio-Android-1.0.0-release.apk
OUT=evidence/phase89-release-smoke
mkdir -p "$OUT"

[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo "Release smoke requires an isolated emulator"; exit 1; }
[[ -s "$APK" ]] || { echo "Missing release APK"; exit 1; }

dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/tcs-window.xml >/dev/null 2>&1
  adb pull /sdcard/tcs-window.xml "$OUT/$name.xml" >/dev/null
}

assert_text() {
  local file="$1" text="$2"
  python3 - "$file" "$text" <<'PY'
import sys,xml.etree.ElementTree as ET
path,text=sys.argv[1:]
root=ET.parse(path).getroot()
vals=[]
for n in root.iter("node"):
    vals += [n.attrib.get("text",""),n.attrib.get("content-desc","")]
if not any(text in v for v in vals):
    raise SystemExit(f"Missing UI text {text!r}; sample={vals[:80]}")
PY
}

tap_text_prefix() {
  local file="$1" prefix="$2"
  local coords
  coords="$(python3 - "$file" "$prefix" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path,prefix=sys.argv[1:]
root=ET.parse(path).getroot()
for n in root.iter("node"):
    value=n.attrib.get("text","") or n.attrib.get("content-desc","")
    if value.startswith(prefix):
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            l,t,r,b=map(int,m.groups())
            print((l+r)//2,(t+b)//2)
            raise SystemExit(0)
raise SystemExit(f"No tappable node starting with {prefix!r}")
PY
)"
  read -r x y <<<"$coords"
  adb shell input tap "$x" "$y"
}

restore() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
}
trap restore EXIT

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK" > "$OUT/install.log"
adb logcat -b crash -c
adb logcat -b events -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" > "$OUT/cold-start.txt"
sleep 2
dump_ui initial
assert_text "$OUT/initial.xml" "Territories"
assert_text "$OUT/initial.xml" "Appearance:"
adb exec-out screencap -p > "$OUT/release-phone-initial.png"

adb shell dumpsys package "$PKG" > "$OUT/package.txt"
python3 - "$OUT/package.txt" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
assert "DEBUGGABLE" not in s, "Release package is debuggable"
assert "TEST_ONLY" not in s, "Release package is testOnly"
assert "android.permission.INTERNET" in s
PY
if adb shell run-as "$PKG" id > "$OUT/run-as.txt" 2>&1; then
  echo "Release package unexpectedly permits run-as" >&2
  exit 1
fi

# Persist a real app-private preference through the release UI, then prove a
# same-signature reinstall preserves it. This avoids privileged access to app data.
tap_text_prefix "$OUT/initial.xml" "Appearance:"
sleep 1
dump_ui appearance-menu
tap_text_prefix "$OUT/appearance-menu.xml" "Dark"
sleep 2
dump_ui dark
assert_text "$OUT/dark.xml" "Appearance: Dark"
adb exec-out screencap -p > "$OUT/release-phone-dark.png"

adb install -r "$APK" > "$OUT/reinstall.log"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" > "$OUT/reinstall-start.txt"
sleep 2
dump_ui after-reinstall
assert_text "$OUT/after-reinstall.xml" "Territories"
assert_text "$OUT/after-reinstall.xml" "Appearance: Dark"
adb exec-out screencap -p > "$OUT/release-phone-after-reinstall.png"

adb shell wm size 1920x1200
adb shell wm density 160
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" > "$OUT/wide-start.txt"
sleep 2
dump_ui wide
assert_text "$OUT/wide.xml" "Territories"
assert_text "$OUT/wide.xml" "Appearance: Dark"
adb exec-out screencap -p > "$OUT/release-wide-dark.png"

adb logcat -b crash -d > "$OUT/crash.log"
adb logcat -b events -d -s am_anr:I > "$OUT/anr.log"
python3 - "$OUT" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1])
crash=(root/"crash.log").read_text(errors="replace")
anr=(root/"anr.log").read_text(errors="replace")
assert "com.koenterprises.territorycardstudio" not in crash, crash
assert "com.koenterprises.territorycardstudio" not in anr, anr
shots={}
for p in sorted(root.glob("*.png")):
    assert p.stat().st_size>1000,p
    shots[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
(root/"release-runtime-status.json").write_text(json.dumps({
    "schema":"tcs-phase9-release-runtime-v1",
    "releaseInstalled":True,
    "coldLaunchPassed":True,
    "nonDebuggable":True,
    "runAsRejected":True,
    "appearancePreferencePersistedAcrossReinstall":True,
    "phoneSmokePassed":True,
    "wideSmokePassed":True,
    "targetCrashFree":True,
    "targetAnrFree":True,
    "screenshots":shots,
    "phase9Complete":False
},indent=2)+"\n")
PY
