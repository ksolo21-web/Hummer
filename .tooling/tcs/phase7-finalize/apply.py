#!/usr/bin/env python3
"""Recover the exact, already-tested label/ancillary implementation.

The former handwritten duplicate patches contained misspelled context lines and
failed before Gradle. The continuation transport produces the same nine production
file hashes and retains its additional regression tests. Reuse that verified path;
do not weaken baseline, transport or result validation to make a patch apply.
This script grants no card approval and does not close Phase 7 or issue #31.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import sys


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply.py <reference-repair-source>")
    target = Path(sys.argv[1]).resolve(strict=True)
    continuation = Path(__file__).resolve().parent.parent / "phase7-continuation" / "apply.py"
    expected = "07d0b15d8c"  # descriptive marker only; the script itself verifies its full transport
    result = subprocess.run(
        [sys.executable, str(continuation), str(target)],
        check=True, capture_output=True, text=True,
    )
    report = json.loads(result.stdout)
    if report.get("filesVerified") != 15 or report.get("phase7Complete") is not False:
        raise RuntimeError("Unexpected continuation integration result")
    if report.get("patchSha256") != "56cb00a489d10d1e14df0fb861452debf0961f88a7354fb85e43c4c190263a56":
        raise RuntimeError("Unexpected continuation patch revision")
    print(json.dumps({
        "schema": 2,
        "filesVerified": report["filesVerified"],
        "patchSha256": report["patchSha256"],
        "phase7Complete": False,
        "approvedRealCards": 0,
        "scope": "Reuse exact tested label and ancillary integration; no source-sufficiency approval",
    }, indent=2))


if __name__ == "__main__":
    main()
