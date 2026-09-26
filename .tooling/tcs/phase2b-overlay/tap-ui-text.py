#!/usr/bin/env python3
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

if len(sys.argv) < 2:
    raise SystemExit("usage: tap-ui-text.py TEXT [--contains]")

target=sys.argv[1]
contains="--contains" in sys.argv[2:]
remote="/sdcard/tcs-tap.xml"
local=Path("/tmp/tcs-tap.xml")
bounds_re=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

for attempt in range(1, 21):
    subprocess.run(["adb","shell","uiautomator","dump",remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    pulled=subprocess.run(["adb","pull",remote,str(local)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if pulled.returncode == 0 and local.exists():
        try:
            root=ET.parse(local).getroot()
            for node in root.iter("node"):
                text=node.attrib.get("text","")
                matched=(target in text) if contains else (target == text)
                if not matched:
                    continue
                m=bounds_re.fullmatch(node.attrib.get("bounds",""))
                if not m:
                    continue
                x1,y1,x2,y2=map(int,m.groups())
                x=(x1+x2)//2
                y=(y1+y2)//2
                subprocess.check_call(["adb","shell","input","tap",str(x),str(y)])
                print(f"TAP_UI_TEXT=PASS target={target!r} attempts={attempt} x={x} y={y}")
                raise SystemExit(0)
        except ET.ParseError:
            pass
    time.sleep(1.5)

raise SystemExit(f"TAP_UI_TEXT=FAIL target={target!r}")
