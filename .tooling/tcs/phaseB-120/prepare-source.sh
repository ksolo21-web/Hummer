#!/usr/bin/env bash
set -euo pipefail
bash .tooling/tcs/phaseA-110/prepare-source.sh
python3 .tooling/tcs/phaseB-120/apply.py tcs-src > evidence/phase-b-overlay.json
