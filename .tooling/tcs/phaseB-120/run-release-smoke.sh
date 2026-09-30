#!/usr/bin/env bash
set -euo pipefail
PKG=com.koenterprises.territorycardstudio
ACT="$PKG/.MainActivity"
APK=evidence/Territory-Android-1.2.0-PhaseB-private.apk
OUT=evidence/phase-b-release
mkdir -p "$OUT"

dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/phase-b-window.xml >/dev/null 2>&1
  adb pull /sdcard/phase-b-window.xml "$OUT/$name.xml" >/dev/null
}
assert_text() {
  python3 - "$1" "$2" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
vals=[]
for n in root.iter("node"):
    vals += [n.attrib.get("text",""), n.attrib.get("content-desc","")]
assert any(needle in v for v in vals),(needle,vals[:120])
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
tap_prefix() {
  read -r x y < <(python3 - "$1" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); prefix=sys.argv[2]
for n in root.iter("node"):
    value=n.attrib.get("text","") or n.attrib.get("content-desc","")
    if value.startswith(prefix):
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            l,t,r,b=map(int,m.groups()); print((l+r)//2,(t+b)//2); raise SystemExit
raise SystemExit("prefix not found: "+prefix)
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

dump_ui home-light
for text in "Territory" "Ready for the field?" "View Territories" "Create Card" "Review Queue"; do
  assert_text "$OUT/home-light.xml" "$text"
done
adb exec-out screencap -p > "$OUT/phaseB-release-home-light.png"

tap_text "$OUT/home-light.xml" "View Territories"
sleep 1
dump_ui territories-light
assert_text "$OUT/territories-light.xml" "Search territories"
assert_text "$OUT/territories-light.xml" "Sort: Number"
adb exec-out screencap -p > "$OUT/phaseB-release-territories-light.png"

tap_prefix "$OUT/territories-light.xml" "Territory "
sleep 1
dump_ui detail-light
assert_text "$OUT/detail-light.xml" "Quick actions"
assert_text "$OUT/detail-light.xml" "Territory facts"
assert_text "$OUT/detail-light.xml" "Source map"
adb exec-out screencap -p > "$OUT/phaseB-release-detail-light.png"

adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" > "$OUT/restart-light.txt"
sleep 1
dump_ui home-again
tap_text "$OUT/home-again.xml" "More"
sleep 1
dump_ui more
assert_text "$OUT/more.xml" "Appearance"
tap_text "$OUT/more.xml" "Dark"
sleep 1
dump_ui more-dark
tap_text "$OUT/more-dark.xml" "Territories"
sleep 1
dump_ui territories-dark
assert_text "$OUT/territories-dark.xml" "Search territories"
adb exec-out screencap -p > "$OUT/phaseB-release-territories-dark.png"
tap_prefix "$OUT/territories-dark.xml" "Territory "
sleep 1
dump_ui detail-dark
assert_text "$OUT/detail-dark.xml" "Quick actions"
adb exec-out screencap -p > "$OUT/phaseB-release-detail-dark.png"

adb install -r "$APK" > "$OUT/reinstall.log"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" > "$OUT/reinstall-start.txt"
sleep 1
dump_ui reinstall
assert_text "$OUT/reinstall.xml" "Territory"

adb shell wm size 1920x1200
adb shell wm density 160
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" > "$OUT/start-wide.txt"
sleep 1
dump_ui wide-home
assert_text "$OUT/wide-home.xml" "View Territories"
tap_text "$OUT/wide-home.xml" "View Territories"
sleep 1
dump_ui wide-territories
assert_text "$OUT/wide-territories.xml" "Search territories"
adb exec-out screencap -p > "$OUT/phaseB-release-wide-dark.png"
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
  "homeLight":True,
  "territoryListLight":True,
  "territoryDetailLight":True,
  "territoryListDark":True,
  "territoryDetailDark":True,
  "reinstall":True,
  "wideDark":True,
  "crashFree":True,
  "anrFree":True,
  "screenshots":shots
},indent=2)+"\n")
PY
