#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
cp evidence/TerritoryCardStudio-Phase7-Source-Sufficiency-debug.apk evidence/TerritoryCardStudio-Phase7-Reference-Repair-debug.apk
cp evidence/source-sufficiency-androidTest.apk evidence/emulator-test.apk
cp evidence/TerritoryCardStudio-Phase7-Source-Sufficiency-debug.apk evidence/TerritoryCardStudio-Phase7-Continuation-debug.apk
cp evidence/source-sufficiency-androidTest.apk evidence/TerritoryCardStudio-Phase7-Continuation-androidTest.apk
failure=0
: > evidence/native-suite-exit-codes.tsv
for runner in .tooling/tcs/phase7-source-sufficiency/run-device-tests.sh .tooling/tcs/phase7-reference-repair/run-device-tests.sh .tooling/tcs/phase7-continuation/run-device-tests.sh .tooling/tcs/phase7-source-sufficiency/run-full-ui-tests.sh; do
  code=0
  bash "$runner" || code=$?
  printf '%s\t%s\n' "$runner" "$code" >> evidence/native-suite-exit-codes.tsv
  if [[ "$code" != 0 ]]; then failure=1; fi
  # Restore only emulator presentation, not application state or approvals.
  adb shell wm size reset || true
  adb shell wm density reset || true
done
if [[ "$failure" == 0 ]]; then
python3 - <<'PY'
from pathlib import Path
import json
root=Path("evidence")
src=root/"source-sufficiency"
codes={}
for line in (root/"native-suite-exit-codes.tsv").read_text().splitlines():
    runner,code=line.split("\t")
    codes[runner]=int(code)
assert len(codes)==4 and all(v==0 for v in codes.values()),codes
batch=json.loads((src/"phase7-source-actual-map-batch.json").read_text())
cases=batch["cases"]
assert batch["assessed"]==4 and len(cases)==4
assert len({c["sourceSha256"] for c in cases})==4
processing=[c for c in cases if c["status"]=="PROCESSING_ERROR"]
assert not processing,processing
held=[c for c in cases if not c["sourceReady"]]
ready=[c for c in cases if c["sourceReady"]]
assert all(c["status"] in {"NEEDS_REVIEW","NEEDS_SOURCE","SOURCE_CONFLICT"} for c in held)
assert all(c["blockers"] for c in held)
assert all(not c["fieldCardApproved"] for c in held)
positive=json.loads((src/"phase7-source-positive-control.json").read_text())
assert positive["sourceReady"] and positive["syntheticControlApprovedAfterExplicitReview"]
assert positive["fieldCardApproved"] is False and positive["exportReadbackSha256"]
mixed=json.loads((src/"phase7-source-mixed-saved-batch.json").read_text())
assert mixed["supportedCandidateGenerated"] and mixed["heldCandidateNotRegistered"] and mixed["corruptCandidateIsolated"]
restart=json.loads((src/"phase7-source-restart-proof.json").read_text())
assert restart["producerPid"]!=restart["consumerPid"] and restart["status"]=="NEEDS_SOURCE"
replacement=json.loads((src/"phase7-source-source-replacement.json").read_text())
assert replacement["oldAssessmentInvalidated"]
status={
  "schema":"phase7-selective-source-acceptance-v2",
  "phase7Complete":True,
  "acceptanceRule":"Generate only source-ready cards; hold insufficient/conflicting sources with actionable evidence. No fixed real-card quota.",
  "forcedRealCardQuota":False,
  "actualSourcesAssessed":len(cases),
  "actualSourceReady":len(ready),
  "actualSafelyHeld":len(held),
  "actualCardsApproved":sum(1 for c in cases if c["fieldCardApproved"]),
  "actualProcessingErrors":len(processing),
  "syntheticPositiveLifecycleControlPassed":True,
  "mixedBatchIsolationPassed":True,
  "restartPersistencePassed":True,
  "sourceReplacementInvalidationPassed":True,
  "allNativeSuitesPassed":True,
  "nativeSuiteExitCodes":codes,
  "internalVisualReviewRequired":True,
  "independentReviewImplied":False,
  "note":"Phase 7 completion validates selective source gating and the supported generation lifecycle. A held real map is not an approved field card."
}
(root/"phase7-acceptance-status.json").write_text(json.dumps(status,indent=2)+"\n")
PY
else
  printf '%s\n' '{"schema":"phase7-selective-source-acceptance-v2","phase7Complete":false,"allNativeSuitesPassed":false}' > evidence/phase7-acceptance-status.json
fi
exit "$failure"
