#!/usr/bin/env python3
"""Bring HAVENLINE's orthographic camera physically closer without weakening runtime gates."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()


def replace_exact(relative: str, old: str, new: str) -> None:
    path = root / relative
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE close-camera patch refused: expected one match in {relative}, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


replace_exact(
    "scripts/core/camera_rig.gd",
    "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n",
    "const CAMERA_OFFSET := Vector3(0.0, 8.4, 8.4)\n",
)
replace_exact(
    "scripts/core/camera_rig.gd",
    "    camera.size = 14.8\n",
    "    camera.size = 13.6\n",
)

camera_source = (root / "scripts/core/camera_rig.gd").read_text(encoding="utf-8")
for marker in (
    "const CAMERA_OFFSET := Vector3(0.0, 8.4, 8.4)",
    "camera.projection = Camera3D.PROJECTION_ORTHOGONAL",
    "camera.size = 13.6",
):
    if marker not in camera_source:
        raise SystemExit(f"HAVENLINE close-camera patch missing marker: {marker}")

print("HAVENLINE close orthographic camera applied")
