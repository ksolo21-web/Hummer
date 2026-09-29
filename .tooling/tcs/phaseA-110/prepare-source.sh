#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
bash .tooling/tcs/phase7-reference-repair/recover.sh
python3 .tooling/tcs/phase7-continuation/apply.py tcs-src > evidence/continuation.json
python3 .tooling/tcs/phase7-continuation/apply-device-test-fix.py tcs-src > evidence/continuation-test.txt
python3 .tooling/tcs/phase7-source-sufficiency/apply.py tcs-src > evidence/source-sufficiency.json
python3 .tooling/tcs/phase7-source-sufficiency/tighten-positive-control.py tcs-src > evidence/positive-control.txt
python3 .tooling/tcs/phase7-source-hardening.py tcs-src > evidence/source-hardening.json
python3 .tooling/tcs/phase7-source-sufficiency/diagnostic-and-test-coverage.py tcs-src > evidence/source-diagnostics.json
python3 .tooling/tcs/phase89/release-hardening.py tcs-src > evidence/release-overlay.json
python3 .tooling/tcs/simple-create-101/apply.py tcs-src > evidence/simple-create-101.json
python3 .tooling/tcs/phaseA-110/apply.py tcs-src > evidence/phase-a-overlay.json
python3 .tooling/tcs/phaseA-110/final-repair.py tcs-src > evidence/phase-a-final-repair.json
