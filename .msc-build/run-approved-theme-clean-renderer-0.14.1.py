#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

# Compatibility entry point only. The reconstruction driver preserves the
# exact-head deterministic finisher in a temporary path before historical
# overlays run. Always invoke that preserved file; never call the repository
# copy because older overlays may have replaced it.
finisher = Path(os.environ.get(
    "MSC_THEME_FINISHER_V3",
    ".msc-build/apply-approved-theme-finish-v3.py",
))
if not finisher.is_file():
    raise SystemExit(f"Deterministic approved-theme finisher is missing: {finisher}")

completed = subprocess.run([sys.executable, str(finisher)], check=False)
raise SystemExit(completed.returncode)
