#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
bash .tooling/tcs/phaseB-120/prepare-source.sh
python3 .tooling/tcs/phaseC-130/apply.py tcs-src > evidence/phase-c-overlay.json
