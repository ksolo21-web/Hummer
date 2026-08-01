#!/usr/bin/env python3
"""Move HAVENLINE's polished orthographic camera inside the strict close-view gate."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
path = root / "scripts/core/camera_rig.gd"
source = path.read_text(encoding="utf-8")
replacements = {
    "const CAMERA_OFFSET := Vector3(0.0, 11.0, 11.0)\n": "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n",
    "    camera.size = 15.5\n": "    camera.size = 14.8\n",
}
for old, new in replacements.items():
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"HAVENLINE camera gate patch expected one match, found {count}: {old!r}")
    source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
if "PROJECTION_ORTHOGONAL" not in source or "CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)" not in source:
    raise SystemExit("HAVENLINE close orthographic camera markers missing")
print("HAVENLINE close orthographic camera gate applied")
