#!/usr/bin/env python3
"""Move HAVENLINE's orthographic camera inside the strict physical-distance gate."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
path = root / "scripts/core/camera_rig.gd"
source = path.read_text(encoding="utf-8")
old = "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n"
new = "const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)\n"
count = source.count(old)
if count != 1:
    raise SystemExit(f"HAVENLINE camera gate patch expected one match, found {count}: {old!r}")
source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
if "PROJECTION_ORTHOGONAL" not in source or "camera.size = 14.8" not in source:
    raise SystemExit("HAVENLINE orthographic framing markers missing")
if "CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)" not in source:
    raise SystemExit("HAVENLINE close physical camera marker missing")
print("HAVENLINE physical camera distance gate applied")
