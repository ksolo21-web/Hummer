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

def node_bounds(node):
    m=bounds_re.fullmatch(node.attrib.get("bounds",""))
    if not m:
        return None
    return tuple(map(int,m.groups()))

for attempt in range(1, 21):
    subprocess.run(["adb","shell","uiautomator","dump",remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    pulled=subprocess.run(["adb","pull",remote,str(local)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if pulled.returncode == 0 and local.exists():
        try:
            tree=ET.parse(local)
            root=tree.getroot()
            parent={child: p for p in root.iter() for child in p}
            for node in root.iter("node"):
                text=node.attrib.get("text","")
                matched=(target in text) if contains else (target == text)
                if not matched:
                    continue

                tap=node
                cur_node=node
                while cur_node in parent:
                    if cur_node.attrib.get("clickable") == "true":
                        tap=cur_node
                        break
                    cur_node=parent[cur_node]
                else:
                    if cur_node.attrib.get("clickable") == "true":
                        tap=cur_node

                b=node_bounds(tap) or node_bounds(node)
                if not b:
                    continue
                x1,y1,x2,y2=b
                x=(x1+x2)//2
                y=(y1+y2)//2
                subprocess.check_call(["adb","shell","input","tap",str(x),str(y)])
                print(
                    "TAP_UI_TEXT=PASS target="+repr(target)+
                    " attempts="+str(attempt)+
                    " clickable="+tap.attrib.get("clickable","false")+
                    " x="+str(x)+" y="+str(y)
                )
                raise SystemExit(0)
        except ET.ParseError:
            pass
    time.sleep(1.5)

raise SystemExit("TAP_UI_TEXT=FAIL target="+repr(target))
