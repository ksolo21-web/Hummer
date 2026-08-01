#!/usr/bin/env python3
"""Patch HAVENLINE's opening spawn and camp composition for a clear gameplay view."""
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
    "    player.position = Vector3(0.0, 0.05, 7.0)\n",
)
replace_exact(
    "scripts/core/camera_rig.gd",
    "    camera.fov = 45.0\n",
    "    camera.fov = 52.0\n",
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
camera_source = camera_source.replace(old_offset, "Vector3(0.0, 15.5, 19.5)")
camera_path.write_text(camera_source, encoding="utf-8")

replace_exact(
    "scripts/world/environment_assembler.gd",
    '    _add_asset("tent", Vector3(-5.7, 0.0, 3.9), deg_to_rad(20.0), 1.05)\n',
    '    _add_asset("tent", Vector3(-8.2, 0.0, 3.4), deg_to_rad(24.0), 0.82)\n',
)
replace_exact(
    "scripts/world/environment_assembler.gd",
    '    _add_asset("tent", Vector3(5.5, 0.0, 4.1), deg_to_rad(-20.0), 1.0)\n',
    '    _add_asset("tent", Vector3(8.2, 0.0, 3.4), deg_to_rad(-24.0), 0.82)\n',
)
replace_exact(
    "scripts/world/environment_assembler.gd",
    '    _add_asset("fence", Vector3(-8.5, 0.0, 4.4), deg_to_rad(90.0), 1.0)\n',
    '    _add_asset("fence", Vector3(-10.2, 0.0, 4.6), deg_to_rad(90.0), 0.9)\n',
)
replace_exact(
    "scripts/world/environment_assembler.gd",
    '    _add_asset("fence", Vector3(8.3, 0.0, 4.2), deg_to_rad(90.0), 1.0)\n',
    '    _add_asset("fence", Vector3(10.2, 0.0, 4.6), deg_to_rad(90.0), 0.9)\n',
)
