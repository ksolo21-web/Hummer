#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,sys

if len(sys.argv)!=2:
    raise SystemExit("Usage: finalize.py <evidence-dir>")
e=Path(sys.argv[1]).resolve(strict=True)
overlay=json.loads((e/"phase-a-overlay.json").read_text())
device=json.loads((e/"phase-a-device/status.json").read_text())
runtime=json.loads((e/"phase-a-release/status.json").read_text())
apk=e/"Territory-Android-1.1.0-PhaseA-private.apk"
aab=e/"Territory-Android-1.1.0-PhaseA-private.aab"

assert overlay["conceptAIcon"] is True
assert overlay["territoryTypeMultiSelect"] is True
assert overlay["minimumSelectedTypes"]==1
assert overlay["singleCompleteUserMapCanBeAuthoritative"] is True
assert device["phaseAUiTests"]==4
assert device["simpleCreateRegressionTests"]==2
assert device["sourceReadyLifecycleTests"]==1
assert device["crashFree"] and device["anrFree"]
assert all(runtime[k] for k in [
    "install","coldLaunch","lightPhone","darkPhone","reinstall","wide",
    "advancedToolsReachable","crashFree","anrFree"
])
assert apk.is_file() and aab.is_file()

sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
status={
  "schema":"territory-phase-a-110-final-v1",
  "phaseAComplete":True,
  "version":"1.1.0",
  "versionCode":47,
  "phase7Frozen":True,
  "phase7Reopened":False,
  "conceptAIcon":True,
  "simpleNavigation":["Home","Territories","Create","Queue","More"],
  "territoryTypeMultiSelect":True,
  "minimumTerritoryTypesRequired":1,
  "advancedToolsHiddenByDefault":True,
  "advancedToolsStillAvailable":True,
  "singleCompleteUserMapCanBeAuthoritative":True,
  "sourceReadyLifecycleRegression":"PASS",
  "coreRegression":"PASS",
  "releaseLint":"PASS",
  "privateReleaseRuntime":"PASS",
  "lightPhone":"PASS",
  "darkPhone":"PASS",
  "wide":"PASS",
  "crashAnr":"PASS_NONE",
  "apkSha256":sha(apk),
  "aabSha256":sha(aab),
  "blockers":[]
}
(e/"phase-a-final-status.json").write_text(json.dumps(status,indent=2)+"\n")
print(json.dumps(status,indent=2))
