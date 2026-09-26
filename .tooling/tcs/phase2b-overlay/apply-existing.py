#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
ui=root/"app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt"
s=ui.read_text()
old="TerritoryWorkspace(modifier, selected, dashboard.revision, onBackToDashboard)"
new="ModeAwareTerritoryWorkspace(modifier, selected, dashboard.revision, onBackToDashboard)"
if s.count(old) != 1:
    raise SystemExit("Phase 2B workspace call anchor mismatch: "+str(s.count(old)))
ui.write_text(s.replace(old,new,1))
print("PHASE2B_UI_TRANSFORM=PASS")
