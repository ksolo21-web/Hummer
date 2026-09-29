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
exit "$failure"
