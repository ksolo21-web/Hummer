#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
ACT="$PKG/.MainActivity"
APK=evidence/Territory-Android-1.1.0-PhaseA-private.apk
OUT=evidence/phase-a-release
mkdir -p "$OUT"

dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/phase-a-window.xml >/dev/null 2>&1
  adb pull /sdcard/phase-a-window.xml "$OUT/$name.xml" >/dev/null
}
assert_text() {
  python3 - "$1" "$2" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
vals=[]
for n in root.iter("node"):
    vals += [n.attrib.get("text",""), n.attrib.get("content-desc","")]
assert any(needle in v for v in vals),(needle,vals[:100])
PY
}
tap_text() {
  read -r x y < <(python3 - "$1" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter("node"):
    value=n.attrib.get("text","") or n.attrib.get("content-desc","")
    if value==target:
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            l,t,r,b=map(int,m.groups()); print((l+r)//2,(t+b)//2); raise SystemExit
raise SystemExit("text not found: "+target)
PY
)
  adb shell input tap "$x" "$y"
}

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK" > "$OUT/install.log"
adb logcat -b crash -c
adb logcat -b events -c
adb shell am start -W -n "$ACT" > "$OUT/start-light.txt"
sleep 2

dump_ui light
for text in "Territory" "View Territories" "Create Card" "Import Map" "Review Queue"; do
  assert_text "$OUT/light.xml" "$text"
done
adb exec-out screencap -p > "$OUT/phaseA-release-light-phone.png"

tap_text "$OUT/light.xml" "More"
sleep 1
dump_ui more
assert_text "$OUT/more.xml" "Appearance"
assert_text "$OUT/more.xml" "Advanced tools"
tap_text "$OUT/more.xml" "Dark"
sleep 2
dump_ui dark
assert_text "$OUT/dark.xml" "More"
adb exec-out screencap -p > "$OUT/phaseA-release-dark-phone.png"

adb install -r "$APK" > "$OUT/reinstall.log"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" > "$OUT/reinstall-start.txt"
sleep 2
dump_ui reinstall
assert_text "$OUT/reinstall.xml" "Territory"

adb shell wm size 1920x1200
adb shell wm density 160
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" > "$OUT/start-wide.txt"
sleep 2
dump_ui wide
assert_text "$OUT/wide.xml" "Territory"
assert_text "$OUT/wide.xml" "View Territories"
adb exec-out screencap -p > "$OUT/phaseA-release-wide.png"
adb shell wm size reset
adb shell wm density reset

adb logcat -b crash -d > "$OUT/crash.log"
adb logcat -b events -d -s am_anr:I > "$OUT/anr.log"
python3 - "$OUT" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1])
assert "com.koenterprises.territorycardstudio" not in (root/"crash.log").read_text(errors="replace")
assert "com.koenterprises.territorycardstudio" not in (root/"anr.log").read_text(errors="replace")
shots={}
for p in sorted(root.glob("*.png")):
    assert p.stat().st_size>1000,p
    shots[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
(root/"status.json").write_text(json.dumps({
  "install":True,
  "coldLaunch":True,
  "lightPhone":True,
  "darkPhone":True,
  "reinstall":True,
  "wide":True,
  "advancedToolsReachable":True,
  "crashFree":True,
  "anrFree":True,
  "screenshots":shots
},indent=2)+"\n")
PY
