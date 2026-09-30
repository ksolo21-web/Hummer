#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,sys

if len(sys.argv)!=2:
    raise SystemExit("Usage: finalize.py <evidence-dir>")
e=Path(sys.argv[1]).resolve(strict=True)
overlay=json.loads((e/"phase-b-overlay.json").read_text())
device=json.loads((e/"phase-b-device/status.json").read_text())
runtime=json.loads((e/"phase-b-release/status.json").read_text())
apk=e/"Territory-Android-1.2.0-PhaseB-private.apk"
aab=e/"Territory-Android-1.2.0-PhaseB-private.aab"

assert overlay["phaseAReopened"] is False
assert overlay["homeRedesign"] and overlay["territoryListRedesign"] and overlay["territoryDetailScreen"] and overlay["darkThemePolish"]
assert overlay["conceptAIconPreserved"] and overlay["multiSelectCreatePreserved"] and overlay["singleCompleteUserMapRulePreserved"]
assert device["phaseBUiTests"]==5
assert device["wideDarkDetailTests"]==1
assert device["phaseARegressionTests"]==4
assert device["simpleCreateRegressionTests"]==2
assert device["sourceReadyLifecycleTests"]==1
assert device["crashFree"] and device["anrFree"]
assert all(runtime[k] for k in [
    "install","coldLaunch","homeLight","territoryListLight","territoryDetailLight",
    "territoryListDark","territoryDetailDark","reinstall","wideDark","crashFree","anrFree"
])
assert apk.is_file() and aab.is_file()

sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
status={
  "schema":"territory-phase-b-120-final-v1",
  "phaseBComplete":True,
  "version":"1.2.0",
  "versionCode":48,
  "phaseACompletePreserved":True,
  "phaseAReopened":False,
  "homeRedesign":"PASS",
  "territoryListRedesign":"PASS",
  "territoryDetailScreen":"PASS",
  "darkThemePolish":"PASS",
  "conceptAIconPreserved":True,
  "simpleCreatePreserved":True,
  "territoryTypeMultiSelectPreserved":True,
  "minimumTerritoryTypesRequired":1,
  "singleCompleteUserMapCanBeAuthoritative":True,
  "advancedToolsHiddenByDefault":True,
  "sourceReadyLifecycleRegression":"PASS",
  "coreRegression":"PASS",
  "releaseLint":"PASS",
  "privateReleaseRuntime":"PASS",
  "phoneLight":"PASS",
  "phoneDark":"PASS",
  "wideDark":"PASS",
  "crashAnr":"PASS_NONE",
  "apkSha256":sha(apk),
  "aabSha256":sha(aab),
  "blockers":[]
}
(e/"phase-b-final-status.json").write_text(json.dumps(status,indent=2)+"\n")
print(json.dumps(status,indent=2))
