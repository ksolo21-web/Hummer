#!/usr/bin/env python3
"""Patch HAVENLINE's opening spawn and camera composition for a clear outpost view."""
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
            f"HAVENLINE composition patch refused: expected one match in {relative}, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


replace_exact(
    "scripts/main.gd",
    "    player.position = Vector3(0.0, 0.05, 5.7)\n",
    "    player.position = Vector3(0.0, 0.05, -1.5)\n",
)
replace_exact(
    "scripts/core/camera_rig.gd",
    "    camera.fov = 45.0\n",
    "    camera.fov = 50.0\n",
)
replace_exact(
    "scripts/core/camera_rig.gd",
    "    camera.far = 115.0\n",
    "    camera.far = 140.0\n",
)
old_offset = "Vector3(10.8, 12.8, 14.4)"
camera_path = root / "scripts/core/camera_rig.gd"
camera_source = camera_path.read_text(encoding="utf-8")
count = camera_source.count(old_offset)
if count != 2:
    raise SystemExit(
        f"HAVENLINE composition patch refused: expected two camera offsets, found {count}"
    )
camera_source = camera_source.replace(old_offset, "Vector3(7.5, 16.0, 20.0)")
camera_path.write_text(camera_source, encoding="utf-8")
