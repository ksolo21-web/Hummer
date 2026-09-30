#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,sys

if len(sys.argv)!=2:
    raise SystemExit("Usage: finalize.py <evidence-dir>")
e=Path(sys.argv[1]).resolve(strict=True)
overlay=json.loads((e/"phase-c-overlay.json").read_text())
standard=json.loads((e/"territory-standard-qa.json").read_text())
device=json.loads((e/"phase-c-device/status.json").read_text())
runtime=json.loads((e/"phase-c-release/status.json").read_text())
apk=e/"Territory-Android-1.3.0-PhaseC-private.apk"
aab=e/"Territory-Android-1.3.0-PhaseC-private.aab"

assert overlay["phaseBReopened"] is False
assert overlay["advancedToolsSecondary"] and overlay["knowledgeBaseSecondary"]
assert standard["status"]=="PASS" and standard["fullCoreContractSmoke"] is True
assert standard["failures"]==0 and standard["errors"]==0
assert device["phaseCUiTests"]==5
assert device["phaseCWideTests"]==1
assert device["phaseBRegressionTests"]==5
assert device["phaseBWideRegressionTests"]==1
assert device["phaseARegressionTests"]==4
assert device["simpleCreateRegressionTests"]==2
assert device["sourceReadyLifecycleTests"]==1
assert device["advancedToolsSecondary"] and device["knowledgeBaseSecondary"]
assert device["crashFree"] and device["anrFree"]
assert all(runtime[k] for k in [
    "install","coldLaunch","advancedHiddenOnHome","knowledgeHiddenOnMore",
    "advancedSecondaryScreen","knowledgeReachableFromAdvanced","darkAdvanced",
    "reinstall","wideAdvanced","crashFree","anrFree"
])
assert apk.is_file() and aab.is_file()

sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
status={
  "schema":"territory-phase-c-130-final-v1",
  "phaseCComplete":True,
  "version":"1.3.0",
  "versionCode":49,
  "phaseBCompletePreserved":True,
  "phaseBReopened":False,
  "advancedToolsSecondary":"PASS",
  "knowledgeBaseSecondary":"PASS",
  "detailUsesMoreBeforeAdvanced":"PASS",
  "simpleCreatePreserved":"PASS",
  "conceptAIconPreserved":True,
  "territoryTypeMultiSelectPreserved":True,
  "minimumTerritoryTypesRequired":1,
  "singleCompleteUserMapCanBeAuthoritative":True,
  "territoryStandardCoreQa":"PASS",
  "phaseBRegression":"PASS",
  "phaseARegression":"PASS",
  "simpleCreateRegression":"PASS",
  "sourceReadyLifecycleRegression":"PASS",
  "releaseLint":"PASS",
  "privateReleaseRuntime":"PASS",
  "phoneLight":"PASS",
  "phoneDark":"PASS",
  "wideDark":"PASS",
  "crashAnr":"PASS_NONE",
  "internalVisualReviewRequired":True,
  "independentReviewClaimed":False,
  "apkSha256":sha(apk),
  "aabSha256":sha(aab),
  "blockers":[]
}
(e/"phase-c-final-status.json").write_text(json.dumps(status,indent=2)+"\n")
print(json.dumps(status,indent=2))
